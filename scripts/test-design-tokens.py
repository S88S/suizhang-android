#!/usr/bin/env python3
"""Keep the machine-readable DESIGN.md token block aligned with Android resources."""
from __future__ import annotations

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / "docs/design/DESIGN.md"
ANDROID = ROOT / "app/src/main"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def require(label: str, ok: bool) -> None:
    if not ok:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)


def xml_values(path: Path, tag: str) -> dict[str, str]:
    root = ET.parse(path).getroot()
    return {node.attrib["name"]: node.text.strip() for node in root.findall(tag)}


def luminance(value: str) -> float:
    raw = value.removeprefix("#")
    channels = [int(raw[index:index + 2], 16) / 255 for index in (0, 2, 4)]
    linear = [channel / 12.92 if channel <= 0.04045 else ((channel + 0.055) / 1.055) ** 2.4 for channel in channels]
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2]


def contrast(foreground: str, background: str) -> float:
    first, second = sorted((luminance(foreground), luminance(background)), reverse=True)
    return (first + 0.05) / (second + 0.05)


def check_dimension_entries(entries: object, values: dict[str, str], path: str = "dimensions") -> int:
    checked = 0
    if isinstance(entries, dict):
        if set(entries) >= {"resource", "value"}:
            resource = entries["resource"]
            expected = entries["value"]
            require(f"{path} -> @dimen/{resource} = {expected}", values.get(resource) == expected)
            return 1
        for key, value in entries.items():
            checked += check_dimension_entries(value, values, f"{path}.{key}")
    return checked


def main() -> int:
    source = DESIGN.read_text(encoding="utf-8")
    blocks = re.findall(r"```yaml\s*\n(.*?)\n```", source, re.DOTALL)
    require("存在且仅存在一个机器可读YAML令牌段", len(blocks) == 1)
    tokens = json.loads(blocks[0])  # JSON is a valid YAML subset, readable without third-party packages.

    palette_files = {
        "light": ANDROID / "res/values/colors.xml",
        "dark": ANDROID / "res/values-night/colors.xml",
    }
    palettes: dict[str, dict[str, str]] = {}
    color_count = 0
    for theme, path in palette_files.items():
        resources = xml_values(path, "color")
        palettes[theme] = {}
        for role, token in tokens["colors"][theme].items():
            resource = token["resource"]
            expected = token["value"].upper()
            actual = resources.get(resource, "").upper()
            require(f"{theme}.{role} -> @color/{resource} = {expected}", actual == expected)
            palettes[theme][role] = actual
            color_count += 1

    dimens = xml_values(ANDROID / "res/values/dimens.xml", "dimen")
    dimension_count = check_dimension_entries(tokens["dimensions"], dimens)
    require("DESIGN.md與Android顏色令牌均有實值", color_count > 0)
    require("DESIGN.md與Android尺寸令牌均有實值", dimension_count > 0)

    for check in tokens["contrast_checks"]:
        theme = check["theme"]
        foreground = palettes[theme][check["foreground"]]
        background = palettes[theme][check["background"]]
        ratio = contrast(foreground, background)
        require(f"{theme} {check['foreground']}/{check['background']} {ratio:.2f}:1 >= {check['minimum']}:1", ratio >= check["minimum"])

    java = (ANDROID / "java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
    raw_colors = re.findall(r"0x[0-9a-fA-F]{6,8}", java)
    require("MainActivity不含硬编码颜色（透明占位除外）", all(value.lower() == "0x00000000" for value in raw_colors))
    require("导航仍为总览/持仓/日历/更多", tokens["layout"]["navigation"] == ["总览", "持仓", "日历", "更多"])
    print(f"通过：{color_count} 个主题颜色、{dimension_count} 个尺寸令牌、{len(tokens['contrast_checks'])} 组对比度检查。")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # Make a failed assertion visible to normal test runners.
        print(error, file=sys.stderr)
        raise
