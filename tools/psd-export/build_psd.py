"""Rebuilds the "Gifts for Her" email design as a layered, editable PSD.

The source artwork was a flat raster, so nothing here is traced from pixels:
the layout is re-authored as shapes and live type layers at 1200x5100 (a 600px
email column at 2x). Photography is left as clearly-named empty slots for the
originals to be dropped into.

Run: python3 build_psd.py [output.psd]
"""

from __future__ import annotations

import sys

from PIL import Image, ImageDraw

from psdlib import CR, Document, Group, Layer, TextStyle, render_text

# ---------------------------------------------------------------------------
# Canvas
# ---------------------------------------------------------------------------

W, H = 1200, 5100
MARGIN = 80

# ---------------------------------------------------------------------------
# Palette
# ---------------------------------------------------------------------------

WHITE = (255, 255, 255)
INK = (20, 20, 20)
BODY = (90, 90, 90)
MUTED = (122, 122, 122)
HERO_PINK = (251, 44, 86)
MAGENTA = (232, 24, 140)
YELLOW = (255, 212, 0)
BTN_RED = (230, 57, 80)
LABEL_RED = (232, 69, 90)
CARD_FRAME = (251, 234, 241)

TINTS = [
    (249, 221, 232),  # pink
    (221, 239, 230),  # mint
    (247, 235, 212),  # cream
    (232, 222, 244),  # lavender
]

# ---------------------------------------------------------------------------
# Fonts
# ---------------------------------------------------------------------------

MONT = "/usr/share/fonts/opentype/montserrat/Montserrat-%s.otf"
INTER = "/usr/share/fonts/opentype/inter/Inter-%s.otf"

MONT_BOLD = (MONT % "Bold", "Montserrat-Bold")
MONT_SEMI = (MONT % "SemiBold", "Montserrat-SemiBold")
INTER_REG = (INTER % "Regular", "Inter-Regular")
INTER_MED = (INTER % "Medium", "Inter-Medium")
INTER_SEMI = (INTER % "SemiBold", "Inter-SemiBold")


def style(font, size, color, leading=None, tracking=0, align="left") -> TextStyle:
    path, ps_name = font
    return TextStyle(
        font_path=path,
        ps_font_name=ps_name,
        size=size,
        color=tuple(color) + (255,),
        leading=leading,
        tracking=tracking,
        align=align,
    )


# ---------------------------------------------------------------------------
# Shape helpers -- drawn supersampled so edges and corners stay smooth
# ---------------------------------------------------------------------------

SS = 4


def _supersampled(width: int, height: int, paint) -> Image.Image:
    big = Image.new("RGBA", (width * SS, height * SS), (0, 0, 0, 0))
    paint(ImageDraw.Draw(big), SS)
    return big.resize((width, height), Image.LANCZOS)


def rounded_rect(width: int, height: int, radius: int, fill) -> Image.Image:
    def paint(draw, s):
        draw.rounded_rectangle(
            (0, 0, width * s - 1, height * s - 1),
            radius=radius * s,
            fill=tuple(fill) + (255,),
        )

    return _supersampled(width, height, paint)


def rect(width: int, height: int, fill) -> Image.Image:
    return Image.new("RGBA", (width, height), tuple(fill) + (255,))


def circle(radius: int, fill) -> Image.Image:
    size = radius * 2

    def paint(draw, s):
        draw.ellipse((0, 0, size * s - 1, size * s - 1), fill=tuple(fill) + (255,))

    return _supersampled(size, size, paint)


def dashed_outline(width: int, height: int, radius: int, color, dash=18, gap=14) -> Image.Image:
    """A dashed guide rectangle, used to mark an empty photo slot."""
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    stroke = tuple(color) + (255,)
    thickness = 3

    def run(length, place):
        pos = radius
        while pos < length - radius:
            end = min(pos + dash, length - radius)
            place(pos, end)
            pos = end + gap

    run(width, lambda a, b: draw.rectangle((a, 0, b, thickness - 1), fill=stroke))
    run(width, lambda a, b: draw.rectangle((a, height - thickness, b, height - 1), fill=stroke))
    run(height, lambda a, b: draw.rectangle((0, a, thickness - 1, b), fill=stroke))
    run(height, lambda a, b: draw.rectangle((width - thickness, a, width - 1, b), fill=stroke))
    return img


def clip_to(image: Image.Image, left: int, top: int, box) -> tuple:
    """Crop a layer to ``box`` (l, t, r, b), returning ``(image, left, top)``."""
    bl, bt, br, bb = box
    x0, y0 = max(left, bl), max(top, bt)
    x1, y1 = min(left + image.width, br), min(top + image.height, bb)
    if x1 <= x0 or y1 <= y0:
        return None, 0, 0
    cropped = image.crop((x0 - left, y0 - top, x1 - left, y1 - top))
    return cropped, x0, y0


# ---------------------------------------------------------------------------
# Text layers
# ---------------------------------------------------------------------------

_JUSTIFY = {"left": 0, "right": 1, "center": 2}


def add_text(parent: Group, name: str, lines, st: TextStyle, origin) -> Layer:
    """Add a live type layer whose pixels match what Photoshop will re-render."""
    image, left, top, _ = render_text(lines, st, origin)
    body = CR.join(lines)
    payload = body + CR

    layer = Layer(
        name=name,
        image=image,
        left=left,
        top=top,
        text={
            "text": body,
            "fonts": [st.ps_font_name],
            "runs": [
                {
                    "length": len(payload),
                    "font_index": 0,
                    "size": float(st.size),
                    "color": st.color[:3],
                    "leading": float(st.line_height),
                    "tracking": int(st.tracking),
                }
            ],
            "justification": _JUSTIFY[st.align],
            "origin": origin,
            "bounds": (
                left - origin[0],
                top - origin[1],
                left + image.width - origin[0],
                top + image.height - origin[1],
            ),
        },
    )
    parent.add(layer)
    return layer


def add_button(parent: Group, label: str, left: int, top: int, width: int, height: int,
               fill, text_color, font, size: float) -> Group:
    group = Group("Button - %s" % label)
    parent.add(group)
    group.add(Layer("Pill", rounded_rect(width, height, height // 2, fill), left, top))
    add_text(
        group,
        "Label - %s" % label,
        [label],
        style(font, size, text_color, align="center"),
        (left + width // 2, top + height // 2 + int(size * 0.36)),
    )
    return group


def add_photo_slot(parent: Group, name: str, left: int, top: int, width: int, height: int,
                   radius: int, tint) -> Group:
    """An empty, clearly-labelled place for real photography."""
    group = Group("PHOTO SLOT - %s" % name)
    parent.add(group)
    group.add(Layer("Tint Base (clip your photo to this)", rounded_rect(width, height, radius, tint), left, top))
    guide = Group("Placeholder Guide (delete once filled)")
    group.add(guide)
    guide.add(Layer("Dashes", dashed_outline(width, height, radius, (150, 150, 150)), left, top))
    add_text(
        guide,
        "Hint",
        ["PLACE PHOTO HERE", name],
        style(INTER_SEMI, 22, (140, 140, 140), leading=34, tracking=120, align="center"),
        (left + width // 2, top + height // 2),
    )
    return group


# ---------------------------------------------------------------------------
# Sections
# ---------------------------------------------------------------------------

HEADER_H = 150
HERO_TOP, HERO_BOTTOM = 150, 960
CARDS_TOP = 1230
CARD_X, CARD_W, CARD_H = 100, 1000, 800
CARD_PITCH = 870
CTA_TOP, CTA_BOTTOM = 4660, 5010

CARDS = [
    {
        "label": "FASHION & ACCESSORIES",
        "title": ["Personalized Perfect Surprise", "Gift Combo"],
        "desc": ["Personalized tumbler + La French perfumes + love card."],
        "slot": "Gift Combo",
    },
    {
        "label": "HAMPERS",
        "title": ["Matcha Moments"],
        "desc": ["Personalized mug, a cute coaster, Masqa dark chocolate", "& a cuddly teddy."],
        "slot": "Matcha Hamper",
    },
    {
        "label": "PERSONALIZED",
        "title": ["Gold Bar Message Necklace"],
        "desc": ["Her initials + a meaningful date engraved on an 18k", "gold-plated bar pendant."],
        "slot": "Gold Necklace",
    },
    {
        "label": "SELF-CARE GIFTS",
        "title": ["Bryan & Candy Bath Kit"],
        "desc": ["Lavender & vanilla body wash, mist and lotion.", "SLS & paraben-free."],
        "slot": "Bath Kit",
    },
]


def build_header(doc: Document) -> None:
    group = doc.add(Group("01 - Header"))
    group.add(Layer("Bar Background", rect(W, HEADER_H, WHITE), 0, 0))
    add_text(group, "Logo - igp", ["igp"], style(MONT_BOLD, 46, HERO_PINK, tracking=-30), (MARGIN, 100))
    add_text(
        group,
        "Nav - Gifts for her",
        ["Gifts for her"],
        style(INTER_SEMI, 22, INK, align="right"),
        (W - MARGIN, 95),
    )


def build_hero(doc: Document) -> None:
    group = doc.add(Group("02 - Hero", open=True))
    band = (0, HERO_TOP, W, HERO_BOTTOM)
    height = HERO_BOTTOM - HERO_TOP
    group.add(Layer("Hero Background", rect(W, height, HERO_PINK), 0, HERO_TOP))

    deco = Group("Decorative Shapes")
    group.add(deco)
    img, left, top = clip_to(circle(100, YELLOW), 1030, 60, band)
    if img:
        deco.add(Layer("Yellow Circle", img, left, top))

    add_photo_slot(group, "Hero Bouquet", 660, 230, 460, 650, 24, (255, 214, 228))

    img, left, top = clip_to(circle(48, MAGENTA), 552, 782, band)
    if img:
        group.add(Layer("Magenta Dot", img, left, top))

    add_text(
        group,
        "Headline",
        ["She's", "everything.", "Gift like it."],
        style(MONT_BOLD, 78, WHITE, leading=86, tracking=-25),
        (MARGIN, 400),
    )
    add_button(group, "Shop Gifts for Her", MARGIN, 640, 320, 70, YELLOW, INK, MONT_SEMI, 26)
    add_text(
        group,
        "Kicker - GIFT THAT FEELING",
        ["GIFT THAT FEELING"],
        style(INTER_SEMI, 20, WHITE, tracking=180),
        (MARGIN, 830),
    )


def build_section_heading(doc: Document) -> None:
    group = doc.add(Group("03 - Section Heading"))
    add_text(
        group,
        "Heading",
        ["Four ways to make her day"],
        style(MONT_BOLD, 60, INK, tracking=-15, align="center"),
        (W // 2, 1105),
    )
    add_text(
        group,
        "Subheading",
        ["Thoughtfully curated. Made to be remembered."],
        style(INTER_REG, 26, BODY, align="center"),
        (W // 2, 1170),
    )


def build_card(doc: Document, index: int, spec: dict) -> None:
    top = CARDS_TOP + index * CARD_PITCH
    group = doc.add(Group("04.%d - Card - %s" % (index + 1, spec["slot"])))

    group.add(Layer("Card Frame", rounded_rect(CARD_W, CARD_H, 30, CARD_FRAME), CARD_X, top))
    group.add(Layer("Card Surface", rounded_rect(CARD_W - 28, CARD_H - 28, 24, WHITE), CARD_X + 14, top + 14))

    photo_x, photo_y = CARD_X + 44, top + 44
    photo_w, photo_h = CARD_W - 88, 410
    add_photo_slot(group, spec["slot"], photo_x, photo_y, photo_w, photo_h, 14, TINTS[index])

    text_x = photo_x
    add_text(
        group,
        "Category - %s" % spec["label"],
        [spec["label"]],
        style(INTER_SEMI, 20, LABEL_RED, tracking=100),
        (text_x, top + 509),
    )

    title_style = style(MONT_BOLD, 42, INK, leading=48, tracking=-10)
    add_text(group, "Title", spec["title"], title_style, (text_x, top + 564))

    desc_top = top + 564 + (len(spec["title"]) - 1) * 48 + 52
    add_text(
        group,
        "Description",
        spec["desc"],
        style(INTER_REG, 24, BODY, leading=34),
        (text_x, desc_top),
    )

    button_top = desc_top + (len(spec["desc"]) - 1) * 34 + 34
    add_button(group, "Shop This Gift", text_x, button_top, 260, 60, BTN_RED, WHITE, INTER_SEMI, 22)


def build_cta(doc: Document) -> None:
    group = doc.add(Group("05 - Closing CTA"))
    band = (0, CTA_TOP, W, CTA_BOTTOM)
    group.add(Layer("CTA Background", rect(W, CTA_BOTTOM - CTA_TOP, MAGENTA), 0, CTA_TOP))

    deco = Group("Decorative Shapes")
    group.add(deco)
    for name, cx, cy, radius, fill in (
        ("Yellow Circle", 140, 4710, 62, YELLOW),
        ("Pink Circle", 1120, 4970, 58, HERO_PINK),
    ):
        img, left, top = clip_to(circle(radius, fill), cx - radius, cy - radius, band)
        if img:
            deco.add(Layer(name, img, left, top))

    add_text(
        group,
        "CTA Headline",
        ["Because she deserves a gift", "as thoughtful as she is."],
        style(MONT_BOLD, 52, WHITE, leading=62, tracking=-15, align="center"),
        (W // 2, CTA_TOP + 110),
    )
    add_button(group, "Explore All Gifts for Her", (W - 360) // 2, CTA_TOP + 215, 360, 68, YELLOW, INK, MONT_SEMI, 24)


def build_footer(doc: Document) -> None:
    group = doc.add(Group("06 - Footer"))
    group.add(Layer("Footer Background", rect(W, H - CTA_BOTTOM, WHITE), 0, CTA_BOTTOM))
    add_text(group, "Logo - igp", ["igp"], style(MONT_BOLD, 40, HERO_PINK, tracking=-30), (MARGIN, H - 40))
    add_text(
        group,
        "Legal",
        ["IGP.com · Gift that Feeling"],
        style(INTER_REG, 20, MUTED, align="right"),
        (W - MARGIN, H - 44),
    )


def build() -> Document:
    doc = Document(W, H, dpi=72, background=WHITE)
    doc.add(Layer("Page Background", rect(W, H, WHITE), 0, 0))
    build_header(doc)
    build_hero(doc)
    build_section_heading(doc)
    for index, spec in enumerate(CARDS):
        build_card(doc, index, spec)
    build_cta(doc)
    build_footer(doc)
    return doc


def main() -> None:
    out = sys.argv[1] if len(sys.argv) > 1 else "gifts-for-her.psd"
    doc = build()
    doc.composite().convert("RGB").save(out.rsplit(".", 1)[0] + "-preview.png")
    doc.save(out)
    print("wrote %s" % out)


if __name__ == "__main__":
    main()
