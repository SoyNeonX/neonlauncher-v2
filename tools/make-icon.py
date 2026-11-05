"""
Genera el icono de NeonLauncher (PNG 512 + .ico multi-tamaño) a partir de la marca NL.
Uso: python tools/make-icon.py <salida.ico> [<salida.png>]
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

SIZE = 512
TOP = np.array([255, 122, 69])     # naranja
BOTTOM = np.array([255, 60, 142])  # magenta
INK = (20, 6, 16, 255)


def gradient(size: int) -> Image.Image:
    y = np.linspace(0.0, 1.0, size).reshape(size, 1, 1)
    x = np.linspace(0.0, 1.0, size).reshape(1, size, 1)
    t = (x * 0.55 + y * 0.45)
    arr = (TOP * (1 - t) + BOTTOM * t).astype(np.uint8)
    return Image.fromarray(arr, "RGB").convert("RGBA")


def rounded_mask(size: int, radius: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def build() -> Image.Image:
    img = gradient(SIZE)
    img.putalpha(rounded_mask(SIZE, 118))

    # Textura de bloques sutil
    blocks = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    bd = ImageDraw.Draw(blocks)
    cell = SIZE // 6
    for row in range(6):
        for col in range(6):
            if (row + col) % 2 == 0:
                bd.rectangle(
                    [col * cell + 3, row * cell + 3, (col + 1) * cell - 3, (row + 1) * cell - 3],
                    fill=(255, 255, 255, 12),
                )
    img = Image.alpha_composite(img, Image.composite(blocks, Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0)),
                                                     rounded_mask(SIZE, 118)))

    draw = ImageDraw.Draw(img)
    font = None
    for candidate in (
        "C:/Windows/Fonts/segoeuib.ttf",
        "C:/Windows/Fonts/arialbd.ttf",
        "C:/Windows/Fonts/seguisb.ttf",
    ):
        if Path(candidate).exists():
            font = ImageFont.truetype(candidate, 250)
            break
    if font is None:
        font = ImageFont.load_default()

    text = "NL"
    left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
    draw.text(
        ((SIZE - (right - left)) / 2 - left, (SIZE - (bottom - top)) / 2 - top - 8),
        text,
        font=font,
        fill=INK,
    )
    return img


def main() -> None:
    out_ico = Path(sys.argv[1] if len(sys.argv) > 1 else "icon.ico")
    out_png = Path(sys.argv[2]) if len(sys.argv) > 2 else None

    icon = build()
    out_ico.parent.mkdir(parents=True, exist_ok=True)
    icon.save(
        out_ico,
        format="ICO",
        sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)],
    )
    print(f"ico -> {out_ico}")
    if out_png:
        icon.resize((256, 256), Image.LANCZOS).save(out_png)
        print(f"png -> {out_png}")


if __name__ == "__main__":
    main()
