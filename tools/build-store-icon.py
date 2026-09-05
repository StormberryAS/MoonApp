#!/usr/bin/env python3
"""Render the store icon that Zapstore and F-Droid display.

WHY THIS EXISTS. The APK ships no raster icon at all: `vectorDrawables
.generatedDensities()` is empty and the launcher icon is an adaptive-icon XML,
because generated density PNGs are a source of build nondeterminism and this
build claims byte reproducibility. Android renders that XML happily. Store
front-ends do not: they look for a bitmap, find `res/BW.xml`, and show an empty
tile. That is exactly what Zapstore was displaying.

The fix is metadata rather than a change to the APK, so reproducibility is
untouched. `zsp` reads Fastlane metadata automatically for GitHub repositories,
and F-Droid reads the same tree, so one file serves both.

    pip install cairosvg pillow
    python3 tools/build-store-icon.py

Output: fastlane/metadata/android/en-US/images/icon.png at 512x512, which is
the size both stores expect.

The composition deliberately mirrors the adaptive icon rather than just scaling
favicon.svg to fill the square: same background colour, same inset, so the store
tile and the launcher icon are the same image. The background is READ FROM
colors.xml rather than hard-coded, because it was hard-coded once and silently
kept SunApp's #080C18 after MoonApp's own colour changed to #0F172A, which is the
drift this docstring claims cannot happen.
"""
import io
import os
import re

import cairosvg
from PIL import Image

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SVG = os.path.join(HERE, "favicon.svg")
OUT = os.path.join(HERE, "fastlane", "metadata", "android", "en-US", "images", "icon.png")

SIZE = 512
# ic_launcher_foreground.xml maps favicon.svg's 64x64 canvas onto 64 of the 108
# adaptive-icon viewport at offset 22, so the mark covers 64/108 of the tile.
# Matching that keeps the store tile and the launcher icon identical rather than
# merely similar.
MARK_FRACTION = 64 / 108
COLORS = os.path.join(HERE, "android", "app", "src", "main", "res", "values", "colors.xml")


def background():
    """res/values/colors.xml -> stormberry_icon_background, as RGBA."""
    xml = open(COLORS, encoding="utf-8").read()
    m = re.search(r'name="stormberry_icon_background">\s*#([0-9A-Fa-f]{6})', xml)
    if not m:
        raise SystemExit(f"stormberry_icon_background not found in {COLORS}")
    h = m.group(1)
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 0xFF)


def main():
    mark_px = round(SIZE * MARK_FRACTION)
    png = cairosvg.svg2png(url=SVG, output_width=mark_px, output_height=mark_px)
    mark = Image.open(io.BytesIO(png)).convert("RGBA")

    canvas = Image.new("RGBA", (SIZE, SIZE), background())
    offset = ((SIZE - mark_px) // 2, (SIZE - mark_px) // 2)
    canvas.alpha_composite(mark, offset)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    canvas.save(OUT, "PNG", optimize=True)

    opaque = sum(1 for p in mark.convert("RGBA").tobytes()[3::4] if p > 0)
    print(f"wrote {OUT}")
    print(f"  {SIZE}x{SIZE}, mark {mark_px}px ({MARK_FRACTION:.0%} of the tile)")
    print(f"  mark has {opaque} opaque pixels; zero would mean the SVG failed to render")
    if opaque == 0:
        raise SystemExit("SVG rendered empty, refusing to ship a blank icon")


if __name__ == "__main__":
    main()
