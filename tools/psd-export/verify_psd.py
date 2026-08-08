"""Re-opens a generated PSD and checks it is structurally sound.

Run: python3 verify_psd.py gifts-for-her.psd
"""

from __future__ import annotations

import sys

import numpy as np
import psd_tools.psd.engine_data as ED
from psd_tools import PSDImage

P = ED.Property


def collect(node, groups, types, rasters):
    for layer in node:
        if layer.is_group():
            groups.append(layer)
            collect(layer, groups, types, rasters)
        elif layer.kind == "type":
            types.append(layer)
        else:
            rasters.append(layer)


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "gifts-for-her.psd"
    psd = PSDImage.open(path)

    groups, types, rasters = [], [], []
    collect(psd, groups, types, rasters)

    failures = []
    print("canvas          : %dx%d" % psd.size)
    print("groups          : %d" % len(groups))
    print("type layers     : %d" % len(types))
    print("raster layers   : %d" % len(rasters))

    for layer in types:
        engine = layer._data.text_data[b"EngineData"].value
        editor = engine[P("EngineDict")][P("Editor")][P("Text")].value
        style_run = engine[P("EngineDict")][P("StyleRun")]
        lengths = [x.value for x in style_run[P("RunLengthArray")]]
        fonts = [f[P("Name")].value for f in engine[P("ResourceDict")][P("FontSet")]]

        if sum(lengths) != len(editor):
            failures.append("%s: run lengths %d != text length %d" % (layer.name, sum(lengths), len(editor)))
        if not layer.text:
            failures.append("%s: no readable text" % layer.name)
        if not fonts:
            failures.append("%s: empty font set" % layer.name)

    fonts_used = set()
    for layer in types:
        engine = layer._data.text_data[b"EngineData"].value
        fonts_used.update(f[P("Name")].value for f in engine[P("ResourceDict")][P("FontSet")])
    print("fonts referenced: %s" % ", ".join(sorted(fonts_used)))

    # Compositing exercises every layer's stored channel data.
    composite = psd.composite()
    if composite.size != psd.size:
        failures.append("composite size %s != canvas %s" % (composite.size, psd.size))
    if np.asarray(composite.convert("RGB")).std() < 1:
        failures.append("composite looks blank")

    if failures:
        print("\nFAILED:")
        for line in failures:
            print("  - %s" % line)
        return 1

    print("\nOK: all %d type layers round-trip; composite renders." % len(types))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
