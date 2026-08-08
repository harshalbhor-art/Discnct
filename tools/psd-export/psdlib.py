"""A small layered-PSD writer built on psd-tools' low-level object model.

psd-tools can parse every structure a PSD needs but offers no authoring API, so
this module assembles the record graph directly: layer groups, raster layers,
clipping masks and -- the useful part -- live type layers whose text stays
editable in Photoshop.

Everything is authored in pixels at 72 DPI, which keeps points and pixels 1:1
and means a font size written here is the size Photoshop reports.
"""

from __future__ import annotations

import io
import struct
from dataclasses import dataclass, field
from typing import Sequence

from PIL import Image, ImageDraw, ImageFont

from psd_tools.constants import (
    BlendMode,
    ChannelID,
    ColorMode,
    Compression,
    SectionDivider,
    Tag,
)
from psd_tools.psd import PSD
from psd_tools.psd.color_mode_data import ColorModeData
from psd_tools.psd.descriptor import (
    Descriptor,
    DescriptorBlock,
    Double,
    Enumerated,
    Integer,
    RawData,
    String,
    Unit,
    UnitFloat,
)
from psd_tools.psd.base import StringElement
from psd_tools.psd.header import FileHeader
from psd_tools.psd.image_data import ImageData
from psd_tools.psd.image_resources import ImageResource, ImageResources
from psd_tools.psd.layer_and_mask import (
    ChannelData,
    ChannelDataList,
    ChannelImageData,
    ChannelInfo,
    GlobalLayerMaskInfo,
    LayerAndMaskInformation,
    LayerFlags,
    LayerInfo,
    LayerRecord,
    LayerRecords,
)
from psd_tools.psd.tagged_blocks import (
    SectionDividerSetting,
    TaggedBlock,
    TaggedBlocks,
    TypeToolObjectSetting,
)

from textengine import CR, build_engine_data

__all__ = ["CR", "Document", "Group", "Layer", "TextStyle", "render_text", "load_font"]

def _block(key: Tag, data) -> TaggedBlock:
    return TaggedBlock(key=key, data=data)


# Channel order Photoshop itself writes: transparency first, then R, G, B.
_CHANNEL_ORDER = (ChannelID.TRANSPARENCY_MASK, ChannelID.CHANNEL_0, ChannelID.CHANNEL_1, ChannelID.CHANNEL_2)


# ---------------------------------------------------------------------------
# Text rendering
# ---------------------------------------------------------------------------


@dataclass
class TextStyle:
    """A character style, sized in pixels."""

    font_path: str
    ps_font_name: str  # PostScript name Photoshop will look up
    size: float
    color: tuple
    leading: float | None = None
    tracking: int = 0  # thousandths of an em, as Photoshop counts it
    align: str = "left"  # left | center | right

    @property
    def line_height(self) -> float:
        return self.leading if self.leading is not None else self.size * 1.2


def load_font(path: str, size: float) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, int(round(size)))


def _line_width(font: ImageFont.FreeTypeFont, line: str, tracking_px: float) -> float:
    if not line:
        return 0.0
    width = font.getlength(line)
    if tracking_px:
        width += tracking_px * len(line)
    return width


def _draw_line(
    draw: ImageDraw.ImageDraw,
    xy: tuple,
    line: str,
    font: ImageFont.FreeTypeFont,
    fill: tuple,
    tracking_px: float,
) -> None:
    """Draw a baseline-anchored line, applying tracking per character."""
    x, y = xy
    if not tracking_px:
        draw.text((x, y), line, font=font, fill=fill, anchor="ls")
        return
    for ch in line:
        draw.text((x, y), ch, font=font, fill=fill, anchor="ls")
        x += font.getlength(ch) + tracking_px


def render_text(lines: Sequence[str], style: TextStyle, origin: tuple) -> tuple:
    """Render text and return ``(rgba_image, left, top, ink_box)``.

    ``origin`` is the baseline anchor of the first line in document
    coordinates: its x meaning follows ``style.align``.
    """
    font = load_font(style.font_path, style.size)
    tracking_px = style.tracking / 1000.0 * style.size
    leading = style.line_height

    widths = [_line_width(font, ln, tracking_px) for ln in lines]
    max_width = max(widths) if widths else 0.0

    pad = int(style.size * 2 + 24)
    canvas_w = int(max_width + pad * 2) + 2
    canvas_h = int((len(lines) - 1) * leading + style.size * 3 + pad * 2)

    # The origin sits wherever the alignment anchors it, so the drawn glyphs
    # stay inside the scratch canvas for centre- and right-aligned text too.
    if style.align == "center":
        local_ox = pad + max_width / 2.0
    elif style.align == "right":
        local_ox = pad + max_width
    else:
        local_ox = float(pad)
    local_oy = float(pad + style.size)

    canvas = Image.new("RGBA", (max(canvas_w, 1), max(canvas_h, 1)), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    for index, line in enumerate(lines):
        if style.align == "center":
            x = local_ox - widths[index] / 2.0
        elif style.align == "right":
            x = local_ox - widths[index]
        else:
            x = local_ox
        _draw_line(draw, (x, local_oy + index * leading), line, font, style.color, tracking_px)

    bbox = canvas.getbbox()
    if bbox is None:  # whitespace-only
        bbox = (0, 0, 1, 1)
    cropped = canvas.crop(bbox)

    doc_left = origin[0] - local_ox + bbox[0]
    doc_top = origin[1] - local_oy + bbox[1]
    return cropped, int(round(doc_left)), int(round(doc_top)), bbox


# ---------------------------------------------------------------------------
# Layer tree
# ---------------------------------------------------------------------------


@dataclass
class Layer:
    name: str
    image: Image.Image | None = None
    left: int = 0
    top: int = 0
    opacity: int = 255
    clipping: bool = False
    visible: bool = True
    blend: BlendMode = BlendMode.NORMAL
    text: dict | None = None  # populated for live type layers


@dataclass
class Group:
    name: str
    children: list = field(default_factory=list)
    opacity: int = 255
    visible: bool = True
    open: bool = False

    def add(self, node):
        self.children.append(node)
        return node


class Document:
    """Collects layers bottom-to-top and serialises them to a PSD."""

    def __init__(self, width: int, height: int, dpi: int = 72, background=(255, 255, 255)):
        self.width = width
        self.height = height
        self.dpi = dpi
        self.background = background
        self.root = Group("root", open=True)

    # -- authoring ---------------------------------------------------------

    def add(self, node):
        return self.root.add(node)

    def add_image(self, name: str, image: Image.Image, left: int, top: int, **kw) -> Layer:
        return self.add(Layer(name=name, image=image.convert("RGBA"), left=left, top=top, **kw))

    # -- compositing -------------------------------------------------------

    def composite(self) -> Image.Image:
        canvas = Image.new("RGBA", (self.width, self.height), self.background + (255,))
        self._paint(self.root, canvas)
        return canvas

    def _paint(self, group: Group, canvas: Image.Image) -> None:
        for node in group.children:
            if isinstance(node, Group):
                if node.visible:
                    self._paint(node, canvas)
            elif node.visible and node.image is not None:
                layer = node.image
                if node.opacity < 255:
                    alpha = layer.getchannel("A").point(lambda v: int(v * node.opacity / 255))
                    layer = layer.copy()
                    layer.putalpha(alpha)
                canvas.alpha_composite(layer, (node.left, node.top))

    # -- serialisation -----------------------------------------------------

    def _flatten(self) -> list:
        """Emit records in PSD file order: bottom-most first."""
        records: list = []

        def walk(group: Group):
            for node in group.children:
                if isinstance(node, Group):
                    records.append(("divider", node))
                    walk(node)
                    records.append(("group", node))
                else:
                    records.append(("layer", node))

        walk(self.root)
        return records

    def _channels_for(self, image: Image.Image | None) -> tuple:
        """Return ``(channel_info, channel_data)`` for one layer."""
        infos, datas = [], []
        if image is None or image.width == 0 or image.height == 0:
            for cid in _CHANNEL_ORDER:
                data = ChannelData(compression=Compression.RAW)
                data.set_data(b"", 0, 0, 8)
                infos.append(ChannelInfo(id=cid, length=2))
                datas.append(data)
            return infos, datas

        width, height = image.size
        bands = {
            ChannelID.TRANSPARENCY_MASK: image.getchannel("A"),
            ChannelID.CHANNEL_0: image.getchannel("R"),
            ChannelID.CHANNEL_1: image.getchannel("G"),
            ChannelID.CHANNEL_2: image.getchannel("B"),
        }
        for cid in _CHANNEL_ORDER:
            data = ChannelData(compression=Compression.RLE)
            data.set_data(bands[cid].tobytes(), width, height, 8)
            infos.append(ChannelInfo(id=cid, length=len(data.data) + 2))
            datas.append(data)
        return infos, datas

    def _type_blocks(self, node: Layer) -> TypeToolObjectSetting:
        spec = node.text
        blob = build_engine_data(
            text=spec["text"],
            font_names=spec["fonts"],
            runs=spec["runs"],
            justification=spec["justification"],
        )

        text_data = DescriptorBlock(version=16, classID=b"TxLr")
        text_data[b"Txt "] = String(spec["text"] if spec["text"].endswith(CR) else spec["text"] + CR)
        text_data[b"textGridding"] = Enumerated(b"textGridding", b"None")
        text_data[b"Ornt"] = Enumerated(b"Ornt", b"Hrzn")
        text_data[b"AntA"] = Enumerated(b"Annt", b"antiAliasSmooth")

        bounds = Descriptor(classID=b"bounds")
        for key, value in zip((b"Left", b"Top ", b"Rght", b"Btom"), spec["bounds"]):
            bounds[key] = UnitFloat(unit=Unit.Points, value=float(value))
        text_data[b"bounds"] = bounds

        bounding_box = Descriptor(classID=b"boundingBox")
        for key, value in zip((b"Left", b"Top ", b"Rght", b"Btom"), spec["bounds"]):
            bounding_box[key] = UnitFloat(unit=Unit.Points, value=float(value))
        text_data[b"boundingBox"] = bounding_box

        text_data[b"TextIndex"] = Integer(0)
        text_data[b"EngineData"] = RawData(blob)

        warp = DescriptorBlock(version=16, classID=b"warp")
        warp[b"warpStyle"] = Enumerated(b"warpStyle", b"warpNone")
        warp[b"warpValue"] = Double(0.0)
        warp[b"warpPerspective"] = Double(0.0)
        warp[b"warpPerspectiveOther"] = Double(0.0)
        warp[b"warpRotate"] = Enumerated(b"Ornt", b"Hrzn")

        ox, oy = spec["origin"]
        return TypeToolObjectSetting(
            version=1,
            transform=(1.0, 0.0, 0.0, 1.0, float(ox), float(oy)),
            text_version=50,
            text_data=text_data,
            warp_version=1,
            warp=warp,
            left=0,
            top=0,
            right=0,
            bottom=0,
        )

    def _resolution_resource(self) -> ImageResource:
        fixed = int(round(self.dpi * 65536))
        data = struct.pack(">IhhIhh", fixed, 1, 1, fixed, 1, 1)
        return ImageResource(key=1005, name="", data=data)

    def build(self) -> PSD:
        records, channel_lists = [], []

        for kind, node in self._flatten():
            blocks = TaggedBlocks()

            if kind == "divider":
                blocks[Tag.SECTION_DIVIDER_SETTING] = _block(
                    Tag.SECTION_DIVIDER_SETTING,
                    SectionDividerSetting(kind=SectionDivider.BOUNDING_SECTION_DIVIDER),
                )
                infos, datas = self._channels_for(None)
                record = LayerRecord(
                    top=0, left=0, bottom=0, right=0,
                    channel_info=infos,
                    blend_mode=BlendMode.NORMAL,
                    opacity=255,
                    clipping=0,
                    flags=LayerFlags(transparency_protected=False, visible=True),
                    name="</Layer group>",
                    tagged_blocks=blocks,
                )
            elif kind == "group":
                blocks[Tag.SECTION_DIVIDER_SETTING] = _block(
                    Tag.SECTION_DIVIDER_SETTING,
                    SectionDividerSetting(
                        kind=SectionDivider.OPEN_FOLDER
                        if node.open
                        else SectionDivider.CLOSED_FOLDER
                    ),
                )
                blocks[Tag.UNICODE_LAYER_NAME] = _block(Tag.UNICODE_LAYER_NAME, StringElement(node.name))
                infos, datas = self._channels_for(None)
                record = LayerRecord(
                    top=0, left=0, bottom=0, right=0,
                    channel_info=infos,
                    blend_mode=BlendMode.NORMAL,
                    opacity=node.opacity,
                    clipping=0,
                    flags=LayerFlags(transparency_protected=False, visible=node.visible),
                    name=node.name,
                    tagged_blocks=blocks,
                )
            else:
                blocks[Tag.UNICODE_LAYER_NAME] = _block(Tag.UNICODE_LAYER_NAME, StringElement(node.name))
                if node.text is not None:
                    blocks[Tag.TYPE_TOOL_OBJECT_SETTING] = _block(
                        Tag.TYPE_TOOL_OBJECT_SETTING, self._type_blocks(node)
                    )
                infos, datas = self._channels_for(node.image)
                width = node.image.width if node.image else 0
                height = node.image.height if node.image else 0
                record = LayerRecord(
                    top=node.top,
                    left=node.left,
                    bottom=node.top + height,
                    right=node.left + width,
                    channel_info=infos,
                    blend_mode=node.blend,
                    opacity=node.opacity,
                    clipping=1 if node.clipping else 0,
                    flags=LayerFlags(transparency_protected=False, visible=node.visible),
                    name=node.name,
                    tagged_blocks=blocks,
                )

            records.append(record)
            channel_lists.append(ChannelDataList(datas))

        header = FileHeader(
            version=1,
            channels=3,
            height=self.height,
            width=self.width,
            depth=8,
            color_mode=ColorMode.RGB,
        )

        layer_info = LayerInfo(
            layer_count=len(records),
            layer_records=LayerRecords(records),
            channel_image_data=ChannelImageData(channel_lists),
        )

        flat = self.composite().convert("RGB")
        image_data = ImageData(compression=Compression.RLE)
        image_data.set_data(
            [flat.getchannel(c).tobytes() for c in "RGB"], header
        )

        return PSD(
            header=header,
            color_mode_data=ColorModeData(),
            image_resources=ImageResources({1005: self._resolution_resource()}),
            layer_and_mask_information=LayerAndMaskInformation(
                layer_info=layer_info,
                global_layer_mask_info=GlobalLayerMaskInfo(),
                tagged_blocks=TaggedBlocks(),
            ),
            image_data=image_data,
        )

    def save(self, path: str) -> None:
        psd = self.build()
        with open(path, "wb") as fp:
            psd.write(fp)
