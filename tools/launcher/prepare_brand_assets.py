#!/usr/bin/env python3
"""Prepare LiYue's launcher icon for the source tree and its published APK."""

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps


def save_icon(image: Image.Image, path: Path, size: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.resize((size, size), Image.Resampling.LANCZOS).save(path, optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--art", required=True, type=Path, help="Square portal artwork")
    parser.add_argument("--leaf", required=True, type=Path, help="Transparent leaf mark")
    parser.add_argument("--res", required=True, type=Path, help="Destination Android resource directory")
    args = parser.parse_args()

    art = ImageOps.fit(Image.open(args.art).convert("RGBA"), (1024, 1024), Image.Resampling.LANCZOS)
    leaf = Image.open(args.leaf).convert("RGBA")
    leaf = leaf.crop(leaf.getchannel("A").getbbox())
    leaf.thumbnail((54, 72), Image.Resampling.LANCZOS)
    mark = Image.new("RGBA", leaf.size, (217, 255, 232, 255))
    mark.putalpha(leaf.getchannel("A"))
    badge = Image.new("RGBA", (96, 96), (0, 0, 0, 0))
    ImageDraw.Draw(badge).ellipse((2, 2, 93, 93), fill="#052628", outline="#73ebbc", width=3)
    badge.alpha_composite(mark, ((badge.width - mark.width) // 2, (badge.height - mark.height) // 2))

    legacy = art.copy()
    legacy.alpha_composite(badge, (32, 32))

    # Android masks the central 72 of 108 adaptive-icon units. Keep the portal
    # in that area, with the dark outer pixels available for the system parallax.
    adaptive = Image.new("RGBA", (1024, 1024), (1, 25, 26, 255))
    portal = art.resize((784, 784), Image.Resampling.LANCZOS)
    mask = Image.new("L", portal.size)
    ImageDraw.Draw(mask).rounded_rectangle((14, 14, 769, 769), radius=112, fill=255)
    from PIL import ImageFilter
    portal.putalpha(mask.filter(ImageFilter.GaussianBlur(30)))
    adaptive.alpha_composite(portal, (120, 120))
    adaptive.alpha_composite(badge, (287, 218))

    save_icon(adaptive, args.res / "drawable/ic_leaf_logo.png", 1900)
    for density, legacy_size, adaptive_size in zip(
        ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"),
        (48, 72, 96, 144, 192),
        (160, 240, 320, 480, 640),
    ):
        save_icon(legacy, args.res / f"mipmap-{density}/ic_launcher.png", legacy_size)
        save_icon(adaptive, args.res / f"drawable-{density}/ic_leaf_logo.png", adaptive_size)
    print(f"Wrote adaptive and legacy color icons to {args.res}")


if __name__ == "__main__":
    main()
