#!/usr/bin/env python3
"""Regenerate the PersonaVault icon set from icon.svg.

The SVG in ``src/main/resources/static/icon.svg`` is the source of truth. This
script derives every raster the app ships, so the assets never drift from the
mark:

    icon.svg      the mark: a person at the centre of a vault door
    icon-512.png  512x512, for the README and anywhere a large mark is needed
    icon-192.png  192x192, referenced from every page head
    favicon.ico   16/32/48, what the browser tab actually shows

16px and 32px are rendered from a simplified variant (no vault ring, no dial
ticks, larger figure) because the detail turns to mush at tab size. 48px and up
use the full mark.

Requires: ``pip install cairosvg pillow``

Usage:
    python scripts/generate-icons.py
"""

from __future__ import annotations

import io
import re
import sys
from pathlib import Path

try:
    import cairosvg
    from PIL import Image
except ImportError:  # pragma: no cover - developer convenience script
    sys.exit("Missing dependency. Run: pip install cairosvg pillow")

STATIC = Path(__file__).resolve().parent.parent / "src/main/resources/static"
SVG = STATIC / "icon.svg"


def simplified(markup: str) -> str:
    """Strips the vault ring and dial ticks and enlarges the figure."""
    out = re.sub(r"  <!-- vault door ring -->.*?</circle>\n", "", markup, flags=re.S)
    out = re.sub(r"  <!-- dial ticks.*?</g>\n", "", out, flags=re.S)
    out = out.replace('r="6.1"', 'r="7.2"').replace('cy="24.2"', 'cy="23.0"')
    return out.replace(
        "M20.2 46.4c0-6.2 5.3-10.6 11.8-10.6s11.8 4.4 11.8 10.6z",
        "M18.6 48.2c0-7.4 6.1-12.6 13.4-12.6s13.4 5.2 13.4 12.6z",
    )


def render(markup: str, size: int) -> Image.Image:
    """Rasterises SVG markup at one square size."""
    buffer = io.BytesIO()
    cairosvg.svg2png(bytestring=markup.encode(), write_to=buffer, output_width=size, output_height=size)
    buffer.seek(0)
    return Image.open(buffer).convert("RGBA")


def main() -> None:
    markup = SVG.read_text(encoding="utf-8")
    small = simplified(markup)

    for name, size in (("icon-512.png", 512), ("icon-192.png", 192)):
        cairosvg.svg2png(url=str(SVG), write_to=str(STATIC / name), output_width=size, output_height=size)
        print(f"wrote {name} ({size}x{size})")

    # A single .ico carries all three sizes; Windows and browsers pick per context.
    render(markup, 48).save(
        STATIC / "favicon.ico", format="ICO", sizes=[(16, 16), (32, 32), (48, 48)]
    )
    print("wrote favicon.ico (16, 32, 48)")


if __name__ == "__main__":
    main()
