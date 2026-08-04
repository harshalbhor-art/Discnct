"""Builds the Photoshop text-engine (``EngineData``) blob for live type layers.

A PSD type layer stores its editable text in a ``TySh`` tagged block, and the
bulk of that block is an ``EngineData`` structure: a nested, token-delimited
document describing the string, the paragraph runs, the character runs and the
document-wide resource tables (font list, style sheets, kinsoku sets).

Photoshop is strict about this structure -- a missing table makes it refuse the
layer -- so the dictionaries below are written out in full rather than trimmed
to the keys we happen to vary.
"""

from __future__ import annotations

import io
from typing import Any, Sequence

import psd_tools.psd.engine_data as ED

# Photoshop separates lines with a carriage return, not a newline.
CR = "\r"


def _ed(value: Any) -> Any:
    """Convert plain Python values into engine-data elements."""
    if isinstance(value, bool):
        return ED.Bool(value)
    if isinstance(value, int):
        return ED.Integer(value)
    if isinstance(value, float):
        return ED.Float(value)
    if isinstance(value, str):
        return ED.String(value)
    if isinstance(value, (list, tuple)):
        return ED.List([_ed(v) for v in value])
    if isinstance(value, dict):
        out = ED.Dict()
        for key, item in value.items():
            out[ED.Property(key)] = _ed(item)
        return out
    raise TypeError("cannot encode %r into engine data" % (type(value),))


def _color(rgb: Sequence[int], alpha: float = 1.0) -> dict:
    """Photoshop stores colours as ARGB floats in 0..1 with ``Type`` 1 = RGB."""
    r, g, b = (c / 255.0 for c in rgb[:3])
    return {"Type": 1, "Values": [float(alpha), r, g, b]}


def _paragraph_properties(justification: int, leading: float | None) -> dict:
    props = {
        "Justification": justification,
        "FirstLineIndent": 0.0,
        "StartIndent": 0.0,
        "EndIndent": 0.0,
        "SpaceBefore": 0.0,
        "SpaceAfter": 0.0,
        "AutoHyphenate": False,
        "HyphenatedWordSize": 6,
        "PreHyphen": 2,
        "PostHyphen": 2,
        "ConsecutiveHyphens": 8,
        "Zone": 36.0,
        "WordSpacing": [0.8, 1.0, 1.33],
        "LetterSpacing": [0.0, 0.0, 0.0],
        "GlyphSpacing": [1.0, 1.0, 1.0],
        "AutoLeading": 1.2,
        "LeadingType": 0,
        "Hanging": False,
        "Burasagari": False,
        "KinsokuOrder": 0,
        "EveryLineComposer": False,
    }
    if leading is not None:
        props["AutoLeading"] = 1.2
    return props


def _style_properties(
    font_index: int,
    size: float,
    color: Sequence[int],
    leading: float | None,
    tracking: int,
    bold: bool,
    caps: bool,
) -> dict:
    """One character-run style. ``Leading`` only applies when AutoLeading is off."""
    props = {
        "Font": font_index,
        "FontSize": float(size),
        "FauxBold": False,
        "FauxItalic": False,
        "AutoLeading": leading is None,
        "Leading": float(leading if leading is not None else size * 1.2),
        "HorizontalScale": 1.0,
        "VerticalScale": 1.0,
        "Tracking": int(tracking),
        "BaselineShift": 0.0,
        "AutoKerning": True,
        "FontCaps": 2 if caps else 0,
        "FontBaseline": 0,
        "Underline": False,
        "Strikethrough": False,
        "Ligatures": True,
        "DLigatures": False,
        "BaselineDirection": 2,
        "Tsume": 0.0,
        "StyleRunAlignment": 2,
        "Language": 0,
        "NoBreak": False,
        "FillColor": _color(color),
        "StrokeColor": _color((0, 0, 0)),
        "FillFlag": True,
        "StrokeFlag": False,
        "FillFirst": True,
        "YUnderline": 1,
        "OutlineWidth": 1.0,
        "CharacterDirection": 0,
        "HindiNumbers": False,
        "Kashida": 1,
        "DiacriticPos": 2,
    }
    return props


def _font_entry(name: str) -> dict:
    return {"Name": name, "Script": 0, "FontType": 1, "Synthetic": 0}


# Photoshop expects these tables to exist even when line-breaking is left at the
# defaults; the values below are the stock "None"/"Photoshop" presets.
_KINSOKU_SET = [
    {
        "Name": "PhotoshopKinsokuHard",
        "NoStart": "、。，．・：；？！",
        "NoEnd": "‘“（〔［｛〈《「『【",
        "Keep": "―‐",
        "Hanging": "、。，．",
    },
    {
        "Name": "PhotoshopKinsokuSoft",
        "NoStart": "、。，．・：；",
        "NoEnd": "‘“（〔［｛〈《「『【",
        "Keep": "―‐",
        "Hanging": "、。，．",
    },
]

_MOJI_KUMI_SET = [
    {"InternalName": "Photoshop6MojiKumiSet1"},
    {"InternalName": "Photoshop6MojiKumiSet2"},
    {"InternalName": "Photoshop6MojiKumiSet3"},
    {"InternalName": "Photoshop6MojiKumiSet4"},
]


def build_engine_data(
    text: str,
    font_names: Sequence[str],
    runs: Sequence[dict],
    justification: int = 0,
    paragraph_leading: float | None = None,
) -> bytes:
    """Serialise a complete ``EngineData`` document.

    ``runs`` is a list of character runs, each a dict with ``length``,
    ``font_index``, ``size``, ``color`` and optionally ``leading``/``tracking``.
    Run lengths must sum to ``len(text)``.
    """
    if not text.endswith(CR):
        text = text + CR

    total = len(text)
    run_lengths = [int(r["length"]) for r in runs]
    if sum(run_lengths) != total:
        # The trailing carriage return belongs to the final run.
        run_lengths[-1] += total - sum(run_lengths)

    style_runs = [
        {
            "StyleSheet": {
                "StyleSheetData": _style_properties(
                    font_index=r.get("font_index", 0),
                    size=r["size"],
                    color=r["color"],
                    leading=r.get("leading"),
                    tracking=r.get("tracking", 0),
                    bold=r.get("bold", False),
                    caps=r.get("caps", False),
                )
            }
        }
        for r in runs
    ]

    # Paragraph runs follow the carriage returns in the text.
    paragraphs = text.split(CR)[:-1]
    para_lengths = [len(p) + 1 for p in paragraphs]
    para_props = _paragraph_properties(justification, paragraph_leading)
    para_runs = [
        {
            "ParagraphSheet": {"DefaultStyleSheet": 0, "Properties": dict(para_props)},
            "Adjustments": {"Axis": [1.0, 0.0, 0.0], "XY": [0.0, 0.0]},
        }
        for _ in para_lengths
    ]

    normal_style = _style_properties(0, 12.0, (0, 0, 0), None, 0, False, False)

    document = {
        "EngineDict": {
            "Editor": {"Text": text},
            "ParagraphRun": {
                "DefaultRunData": {
                    "ParagraphSheet": {"DefaultStyleSheet": 0, "Properties": {}},
                    "Adjustments": {"Axis": [1.0, 0.0, 0.0], "XY": [0.0, 0.0]},
                },
                "RunArray": para_runs,
                "RunLengthArray": para_lengths,
                "IsJoinable": 1,
            },
            "StyleRun": {
                "DefaultRunData": {"StyleSheet": {"StyleSheetData": {}}},
                "RunArray": style_runs,
                "RunLengthArray": run_lengths,
                "IsJoinable": 2,
            },
            "GridInfo": {
                "GridIsOn": False,
                "ShowGrid": False,
                "GridSize": 18.0,
                "GridLeading": 22.0,
                "GridColor": _color((0, 0, 0)),
                "GridLeadingFillColor": _color((0, 0, 0)),
                "AlignLineHeightToGridFlags": False,
            },
            "AntiAlias": 4,
            "UseFractionalGlyphWidths": True,
            "Rendered": {
                "Version": 1,
                "Shapes": {
                    "WritingDirection": 0,
                    "Children": [
                        {
                            "ShapeType": 0,
                            "Procession": 0,
                            "Lines": {"WritingDirection": 0, "Children": []},
                            "Cookie": {
                                "Photoshop": {
                                    "ShapeType": 0,
                                    "PointBase": [0.0, 0.0],
                                    "Base": {
                                        "ShapeType": 0,
                                        "TransformPoint0": [1.0, 0.0],
                                        "TransformPoint1": [0.0, 1.0],
                                        "TransformPoint2": [0.0, 0.0],
                                    },
                                }
                            },
                        }
                    ],
                },
            },
        },
        "ResourceDict": {
            "KinsokuSet": _KINSOKU_SET,
            "MojiKumiSet": _MOJI_KUMI_SET,
            "TheNormalStyleSheet": 0,
            "TheNormalParagraphSheet": 0,
            "ParagraphSheetSet": [
                {
                    "Name": "Normal RGB",
                    "DefaultStyleSheet": 0,
                    "Properties": dict(para_props),
                }
            ],
            "StyleSheetSet": [{"Name": "Normal RGB", "StyleSheetData": normal_style}],
            "FontSet": [_font_entry(n) for n in font_names],
            "SuperscriptSize": 0.583,
            "SuperscriptPosition": 0.333,
            "SubscriptSize": 0.583,
            "SubscriptPosition": 0.333,
            "SmallCapSize": 0.7,
        },
        "DocumentResources": {
            "KinsokuSet": _KINSOKU_SET,
            "MojiKumiSet": _MOJI_KUMI_SET,
            "TheNormalStyleSheet": 0,
            "TheNormalParagraphSheet": 0,
            "ParagraphSheetSet": [
                {
                    "Name": "Normal RGB",
                    "DefaultStyleSheet": 0,
                    "Properties": dict(para_props),
                }
            ],
            "StyleSheetSet": [{"Name": "Normal RGB", "StyleSheetData": normal_style}],
            "FontSet": [_font_entry(n) for n in font_names],
            "SuperscriptSize": 0.583,
            "SuperscriptPosition": 0.333,
            "SubscriptSize": 0.583,
            "SubscriptPosition": 0.333,
            "SmallCapSize": 0.7,
        },
    }

    engine = ED.EngineData()
    for key, value in document.items():
        engine[ED.Property(key)] = _ed(value)

    buf = io.BytesIO()
    engine.write(buf)
    return buf.getvalue()
