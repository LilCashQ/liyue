#!/usr/bin/env python3
"""Recolour and re-brand the already-patched launcher APK (black/white Google look).

Takes the signed 1.6.1 brand APK, replaces the icon art with the LiYue cube,
remaps the green Material palette to the LiYue palette inside resources.arsc,
and re-signs with the LiYue keystore.
"""
from __future__ import annotations

import io
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

SRC = Path("/tmp/liyue-brand.daXbvk/liyue-launcher-1.6.1-arm64.apk")
OUT_DIR = Path("/tmp/liyue-launcher-v2")
ICON_DIR = Path("/root/icon")
BUILD_TOOLS = Path("/opt/android-sdk/build-tools/35.0.0")
KEYSTORE = Path("/root/liyue/keystore/liyue-release.jks")
ALIAS = "liyue"
STORE_PASS = "liyue2026"

# resource entry name in the APK -> our replacement file
ICON_MAP = {
    "res/FU.png": ICON_DIR / "chest_xxxhdpi.png",
    "res/p7.png": ICON_DIR / "chest_mdpi.png",
    "res/Ip.png": ICON_DIR / "chest_hdpi.png",
    "res/OG.png": ICON_DIR / "chest_xhdpi.png",
    "res/xY.png": ICON_DIR / "chest_xxhdpi.png",
    "res/Hv.png": ICON_DIR / "chest_xxxhdpi.png",
    "res/9w.png": ICON_DIR / "chest_mdpi.png",
    "res/yn.png": ICON_DIR / "chest_hdpi.png",
    "res/FS.png": ICON_DIR / "chest_xhdpi.png",
    "res/RJ.png": ICON_DIR / "chest_xxhdpi.png",
    "res/o-.png": ICON_DIR / "chest_xxxhdpi.png",
}

# 字体替换：启动器内置思源黑体 -> 像素字体 Zpix
FONT_ENTRY = "res/xZ.ttf"
FONT_SRC = ICON_DIR / "zpix.ttf"

# green Material palette -> LiYue black/white + single blue accent
COLOR_MAP = {
    0xFF4AE0A0: 0xFF5FA83C,   # 强调 -> 草方块绿
    0xFF1B5E20: 0xFF3F3F3F,   # 深绿 -> GUI 深灰
    0xFFE8F5E9: 0xFFC6C6C6,   # 浅绿底 -> GUI 面板灰
    0xFF008577: 0xFF4A8A2C,   # 青绿 -> 深草绿
    0xFF03DAC6: 0xFF7BC753,   # 次级青 -> 亮草绿
    0xFFF6F3EA: 0xFF4C9A2A,   # 米色底 -> 草方块背景
    0xFF121212: 0xFF2B2B2B,   # 深灰
    0xFF1A73E8: 0xFF5FA83C,   # 上一版蓝色 -> 草绿
    0xFF202124: 0xFF3F3F3F,
}


def recolour_arsc(data: bytes) -> tuple[bytes, int]:
    buf = bytearray(data)
    hits = 0
    for old, new in COLOR_MAP.items():
        needle = old.to_bytes(4, "little")
        replacement = new.to_bytes(4, "little")
        start = 0
        while True:
            idx = buf.find(needle, start)
            if idx < 0:
                break
            buf[idx:idx + 4] = replacement
            hits += 1
            start = idx + 4
    return bytes(buf), hits


def main() -> None:
    if not SRC.is_file():
        raise SystemExit(f"missing source apk: {SRC}")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    unsigned = OUT_DIR / "launcher-unsigned.apk"

    with zipfile.ZipFile(SRC) as zin:
        names = zin.namelist()
        missing = [p for p in ICON_MAP if p not in names]
        if missing:
            raise SystemExit(f"icon entries not found: {missing}")
        hits = 0
        with zipfile.ZipFile(unsigned, "w", zipfile.ZIP_DEFLATED) as zout:
            for info in zin.infolist():
                data = zin.read(info.filename)
                if info.filename == "resources.arsc":
                    data, hits = recolour_arsc(data)
                elif info.filename in ICON_MAP:
                    data = ICON_MAP[info.filename].read_bytes()
                elif info.filename == FONT_ENTRY:
                    data = FONT_SRC.read_bytes()
                elif info.filename.startswith("META-INF/") and info.filename.endswith((".RSA", ".SF", ".MF")):
                    continue
                zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
                # 保持原压缩方式：resources.arsc 与 classes.dex 在 targetSdk 30+ 必须 STORED
                zi.compress_type = info.compress_type
                zi.external_attr = info.external_attr
                zout.writestr(zi, data)
    print(f"recoloured {hits} colour cells")

    aligned = OUT_DIR / "launcher-aligned.apk"
    subprocess.run([str(BUILD_TOOLS / "zipalign"), "-p", "-f", "4", str(unsigned), str(aligned)], check=True)

    signed = OUT_DIR / "liyue-launcher-1.6.2-arm64.apk"
    subprocess.run([
        str(BUILD_TOOLS / "apksigner"), "sign",
        "--ks", str(KEYSTORE), "--ks-key-alias", ALIAS,
        "--ks-pass", f"pass:{STORE_PASS}", "--key-pass", f"pass:{STORE_PASS}",
        "--out", str(signed), str(aligned),
    ], check=True)
    subprocess.run([str(BUILD_TOOLS / "apksigner"), "verify", "--print-certs", str(signed)], check=True)
    print(f"SIGNED {signed} {signed.stat().st_size}")


if __name__ == "__main__":
    main()
