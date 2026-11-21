#!/usr/bin/env python3
"""Apply LiYue branding to the published LeviLaunchroidUnlocked 1.6.0 APK."""

from __future__ import annotations

import argparse
import copy
import hashlib
import struct
import zipfile
from dataclasses import dataclass
from pathlib import Path

UPSTREAM_SHA256 = "e79e87e0c5d3d9ae943e63d3a3755f165571d9b8808a8c9626c35e45f93cba1e"
APP_NAME = "里悦启动器"
SPLASH = "org.levimc.launcher.ui.activities.SplashActivity"
START_ACTION = "com.liyue.launcher.action.START"
VERSION_CODE = 59596200
VERSION_NAME = "1.6.1"

STRING_POOL = 0x0001
XML_RESOURCE_MAP = 0x0180
START_ELEMENT = 0x0102
END_ELEMENT = 0x0103
TABLE_PACKAGE = 0x0200
TABLE_TYPE = 0x0201
NO_INDEX = 0xFFFFFFFF
TYPE_STRING = 0x03
DENSITIES = ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

# The published APK shortens resource paths. The values are the source paths for
# mipmap/ic_launcher and drawable/ic_leaf_logo in the corresponding release.
ICON_PATHS = {"res/FU.png": "drawable/ic_leaf_logo.png"}
ICON_PATHS.update({
    f"res/{name}.png": f"drawable-{density}/ic_leaf_logo.png"
    for name, density in zip(("p7", "Ip", "OG", "xY", "Hv"), DENSITIES)
})
ICON_PATHS.update({
    f"res/{name}.png": f"mipmap-{density}/ic_launcher.png"
    for name, density in zip(("9w", "yn", "FS", "RJ", "o-"), DENSITIES)
})
ADAPTIVE_ICON = "res/BW.xml"


def u16(data, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def u32(data, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def put32(data: bytearray, offset: int, value: int) -> None:
    struct.pack_into("<I", data, offset, value)


@dataclass(frozen=True)
class Chunk:
    kind: int
    base: int
    header: int
    end: int

    @property
    def fields(self) -> int:
        return self.base + self.header


def chunks(data, start: int, end: int):
    while start < end:
        if start + 8 > end:
            raise ValueError(f"Truncated Android resource header at {start:#x}")
        kind, header, size = struct.unpack_from("<HHI", data, start)
        if not 8 <= header <= size or start + size > end:
            raise ValueError(f"Invalid Android resource chunk at {start:#x}")
        yield Chunk(kind, start, header, start + size)
        start += size


def container(data, kind: int) -> Chunk:
    roots = list(chunks(data, 0, len(data)))
    if len(roots) != 1 or roots[0].kind != kind:
        raise ValueError(f"Expected Android resource root {kind:#x}")
    return roots[0]


def unpack_pool_length(data, start: int, unit: int) -> tuple[int, int]:
    first = data[start] if unit == 1 else u16(data, start)
    bit = 1 << (unit * 8 - 1)
    if first & bit:
        second = data[start + unit] if unit == 1 else u16(data, start + unit)
        return ((first & (bit - 1)) << (unit * 8)) | second, start + 2 * unit
    return first, start + unit


def pool_string(data, pool: Chunk, index: int) -> str:
    flags, start = struct.unpack_from("<2I", data, pool.base + 16)
    utf8 = bool(flags & 0x100)
    offset = u32(data, pool.fields + index * 4)
    length, pos = unpack_pool_length(data, pool.base + start + offset, 1 if utf8 else 2)
    if utf8:
        length, pos = unpack_pool_length(data, pos, 1)
        return bytes(data[pos:pos + length]).decode("utf-8", errors="surrogatepass")
    return bytes(data[pos:pos + 2 * length]).decode("utf-16le", errors="surrogatepass")


def pool_strings(data, pool: Chunk) -> list[str]:
    return [pool_string(data, pool, index) for index in range(u32(data, pool.base + 8))]


def append_strings(data: bytearray, pool: Chunk, strings: list[str]) -> bytes:
    count, style_count, flags, start, style_start = struct.unpack_from("<5I", data, pool.base + 8)
    entry_offsets = bytearray(data[pool.fields:pool.fields + 4 * count])
    # Existing string and style offsets remain relative to their respective data blocks.
    styles_and_padding = data[pool.fields + 4 * count:pool.base + start]
    string_end = pool.base + style_start if style_start else pool.end
    string_data = bytearray(data[pool.base + start:string_end])
    style_data = data[string_end:pool.end]
    utf8 = bool(flags & 0x100)
    for string in strings:
        utf16 = string.encode("utf-16le")
        utf8_bytes = string.encode("utf-8")
        length = len(utf16) // 2
        if length >= 128 or (utf8 and len(utf8_bytes) >= 128):
            raise ValueError("Branding strings must use short string-pool lengths")
        entry_offsets.extend(struct.pack("<I", len(string_data)))
        if utf8:
            string_data.extend(bytes((length, len(utf8_bytes))) + utf8_bytes + b"\0")
        else:
            string_data.extend(struct.pack("<H", length) + utf16 + b"\0\0")
    string_data.extend(b"\0" * (-len(string_data) % 4))
    new_start = start + 4 * len(strings)
    new_style_start = new_start + len(string_data) if style_start else 0
    new_size = new_start + len(string_data) + len(style_data)
    header = bytearray(data[pool.base:pool.fields])
    put32(header, 4, new_size)
    struct.pack_into("<5I", header, 8, count + len(strings), style_count, flags & ~1, new_start, new_style_start)
    return bytes(header + entry_offsets + styles_and_padding + string_data + style_data)


def replace_pool(data: bytearray, pool: Chunk, strings: list[str]) -> bytes:
    data[pool.base:pool.end] = append_strings(data, pool, strings)
    put32(data, 4, len(data))
    return bytes(data)


def patch_table(original: bytes) -> bytes:
    data = bytearray(original)
    root = container(data, 0x0002)
    pool, package = list(chunks(data, root.fields, root.end))
    if pool.kind != STRING_POOL or package.kind != TABLE_PACKAGE or u32(data, package.base + 8) != 0x7F:
        raise ValueError("Unexpected launcher resource table layout")
    strings = pool_strings(data, pool)
    new_index = len(strings)
    changes = 0
    for part in chunks(data, package.fields, package.end):
        if part.kind != TABLE_TYPE or data[part.base + 8] != 0x13:
            continue
        if data[part.base + 9] != 0:
            raise ValueError("Unexpected launcher string entry offset encoding")
        entry_index = 0x67  # 0x7f130067 string/app_name
        if entry_index >= u32(data, part.base + 12):
            continue
        displacement = u32(data, part.fields + entry_index * 4)
        if displacement == NO_INDEX:
            continue
        entry = part.base + u32(data, part.base + 16) + displacement
        value = entry + u16(data, entry)
        if data[value + 3] != TYPE_STRING:
            raise ValueError("Launcher name is not a simple string resource")
        old_name = strings[u32(data, value + 4)]
        if old_name not in ("LeviLauncher", "LeviLauncher Unlocked"):
            raise ValueError(f"Unexpected launcher name {old_name!r}")
        put32(data, value + 4, new_index)
        changes += 1
    if changes != 11:
        raise ValueError(f"Expected 11 localized launcher names, found {changes}")
    return replace_pool(data, pool, [APP_NAME])


class BinaryXml:
    def __init__(self, original: bytes):
        self.data = bytearray(original)
        root = container(self.data, 0x0003)
        self.children = list(chunks(self.data, root.fields, root.end))
        self.pool = self.children[0]
        if self.pool.kind != STRING_POOL:
            raise ValueError("Binary XML is missing its string pool")
        self.strings = pool_strings(self.data, self.pool)
        self.additions: list[str] = []
        mapping = next(part for part in self.children if part.kind == XML_RESOURCE_MAP)
        self.resource_ids = {
            index: u32(self.data, offset)
            for index, offset in enumerate(range(mapping.fields, mapping.end, 4))
        }

    def index_for(self, text: str) -> int:
        if text in self.strings:
            return self.strings.index(text)
        if text not in self.additions:
            self.additions.append(text)
        return len(self.strings) + self.additions.index(text)

    def elements(self):
        path: list[tuple[str, dict[int, int]]] = []
        for part in self.children[1:]:
            if part.kind == END_ELEMENT:
                path.pop()
            elif part.kind == START_ELEMENT:
                ext = part.fields
                name = self.strings[u32(self.data, ext + 4)]
                start, size, count = struct.unpack_from("<3H", self.data, ext + 8)
                if size < 20 or ext + start + size * count > part.end:
                    raise ValueError(f"Invalid attribute array for {name}")
                attrs = {}
                for index in range(count):
                    offset = ext + start + index * size
                    res_id = self.resource_ids.get(u32(self.data, offset + 4))
                    if res_id is not None:
                        attrs[res_id] = offset
                path.append((name, attrs))
                yield tuple(path)

    def string_value(self, offset: int | None) -> str | None:
        if offset is not None and self.data[offset + 15] == TYPE_STRING:
            return self.strings[u32(self.data, offset + 16)]
        return None

    def set_string(self, offset: int, text: str) -> None:
        if self.data[offset + 15] != TYPE_STRING:
            raise ValueError("Expected a string XML attribute")
        index = self.index_for(text)
        if u32(self.data, offset + 8) != NO_INDEX:
            put32(self.data, offset + 8, index)
        put32(self.data, offset + 16, index)

    def finish(self) -> bytes:
        if self.additions:
            return replace_pool(self.data, self.pool, self.additions)
        return bytes(self.data)


def patch_manifest(original: bytes) -> bytes:
    xml = BinaryXml(original)
    name_id = 0x01010003
    code_id = 0x0101021B
    version_id = 0x0101021C
    replacements: dict[str, int] = {}
    version_updated = False
    for path in xml.elements():
        tag, attrs = path[-1]
        if len(path) == 1 and tag == "manifest":
            code = attrs[code_id]
            if xml.data[code + 15] != 0x10 or u32(xml.data, code + 16) != 57441315:
                raise ValueError("Unexpected upstream version code")
            if xml.string_value(attrs[version_id]) != "1.6.0":
                raise ValueError("Unexpected upstream version name")
            put32(xml.data, code + 16, VERSION_CODE)
            xml.set_string(attrs[version_id], VERSION_NAME)
            version_updated = True
        if len(path) < 3 or path[-2][0] != "intent-filter" or path[-3][0] != "activity":
            continue
        if xml.string_value(path[-3][1].get(name_id)) != SPLASH:
            continue
        old = xml.string_value(attrs.get(name_id))
        if tag == "action" and old == "android.intent.action.MAIN":
            replacements[START_ACTION] = attrs[name_id]
        if tag == "category" and old == "android.intent.category.LAUNCHER":
            replacements["android.intent.category.DEFAULT"] = attrs[name_id]
    if not version_updated or len(replacements) != 2:
        raise ValueError("Expected launcher version and both desktop intent attributes")
    for text, offset in replacements.items():
        xml.set_string(offset, text)
    return xml.finish()


def patch_adaptive_icon(original: bytes) -> bytes:
    xml = BinaryXml(original)
    insets = []
    for path in xml.elements():
        if tuple(tag for tag, _ in path) == ("adaptive-icon", "foreground", "inset"):
            offset = path[-1][1][0x010104B5]
            if xml.data[offset + 15] != 0x06:
                raise ValueError("Expected a fraction for the adaptive icon inset")
            put32(xml.data, offset + 8, NO_INDEX)
            put32(xml.data, offset + 16, 0)  # 0% of the adaptive foreground layer
            insets.append(offset)
    if len(insets) != 1:
        raise ValueError("Expected one adaptive foreground inset")
    return xml.finish()


def is_v1_signature(path: str) -> bool:
    upper = path.upper()
    return upper.startswith("META-INF/") and (
        upper == "META-INF/MANIFEST.MF" or upper.endswith((".SF", ".RSA", ".DSA", ".EC"))
    )


def patch_apk(source: Path, output: Path, res: Path) -> None:
    if source.resolve() == output.resolve() or output.exists():
        raise ValueError("Choose a new output APK path")
    if hashlib.sha256(source.read_bytes()).hexdigest() != UPSTREAM_SHA256:
        raise ValueError("Input must be the GitHub release APK pinned in this script")
    replacements = {name: (res / relative).read_bytes() for name, relative in ICON_PATHS.items()}
    with zipfile.ZipFile(source) as upstream:
        replacements["resources.arsc"] = patch_table(upstream.read("resources.arsc"))
        replacements["AndroidManifest.xml"] = patch_manifest(upstream.read("AndroidManifest.xml"))
        replacements[ADAPTIVE_ICON] = patch_adaptive_icon(upstream.read(ADAPTIVE_ICON))
        if not replacements.keys() <= set(upstream.namelist()):
            raise ValueError("APK is missing one or more launcher branding entries")
        with zipfile.ZipFile(output, "x") as patched:
            for entry in upstream.infolist():
                name = entry.filename
                if not is_v1_signature(name):
                    payload = replacements[name] if name in replacements else upstream.read(entry)
                    patched.writestr(copy.copy(entry), payload)
        with zipfile.ZipFile(output) as patched:
            for name in patched.namelist():
                if name not in replacements and patched.read(name) != upstream.read(name):
                    raise ValueError(f"Unexpected change to {name}")
    print(f"Wrote {output} with {len(replacements)} branded entries")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path, help="Original upstream release APK")
    parser.add_argument("--output", required=True, type=Path, help="New unsigned APK")
    parser.add_argument("--assets", required=True, type=Path, help="Branded app/src/main/res directory")
    args = parser.parse_args()
    patch_apk(args.input, args.output, args.assets)


if __name__ == "__main__":
    main()
