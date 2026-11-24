#!/usr/bin/env python3
"""启动器图标资源链静态校验（自适应图标 + 圆形图标 + 各密度回退）。

只做源码级断言：真机上的裁切效果必须在设备上目视确认。
"""
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
A = "{http://schemas.android.com/apk/res/android}"

def require(name: str, condition: bool) -> None:
    if not condition:
        raise AssertionError(f"FAIL {name}")
    print(f"PASS {name}")

# 1) Manifest 指向 mipmap 自适应图标，并声明 roundIcon
manifest = (root / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
require("免安装图标改用 mipmap 自适应图标", 'android:icon="@mipmap/ic_launcher"' in manifest)
require("声明圆形图标", 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest)
require("不再指向旧 drawable 位图图标", 'android:icon="@drawable/ic_launcher"' not in manifest)

# 2) 自适应图标声明完整：background + foreground + monochrome
for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    p = res / "mipmap-anydpi-v26" / name
    require(f"{name} 存在", p.is_file())
    node = ET.parse(p).getroot()
    require(f"{name} 是 adaptive-icon", node.tag == "adaptive-icon")
    children = {child.tag for child in node}
    require(f"{name} 含 background", "background" in children)
    require(f"{name} 含 foreground", "foreground" in children)
    # monochrome 是 Android 13+ 主题图标；可缺省，缺省时会退化为前景层裁切
    print(f"INFO {name} monochrome={'monochrome' in children}")

# 3) 被引用的图层资源都真实存在
require("前景层资源存在", (res / "drawable/ic_launcher_foreground.xml").is_file())
require("单色层资源存在", (res / "drawable/ic_launcher_monochrome.xml").is_file())
bg = res / "values/ic_launcher_background.xml"
require("背景色资源存在", bg.is_file())
tree = ET.parse(bg).getroot()
colors = {c.get("name"): (c.text or "").strip() for c in tree.findall("color")}
require("背景色已定义", "ic_launcher_background" in colors)
require("背景色为品牌暖麦金 #8A5E0B", colors.get("ic_launcher_background", "").upper() == "#8A5E0B")

# 4) 渐变/矢量层不含写死 dp 之外的问题：路径非空
for layer in ("drawable/ic_launcher_foreground.xml", "drawable/ic_launcher_monochrome.xml"):
    node = ET.parse(res / layer).getroot()
    require(f"{layer} 是 vector", node.tag == "vector")
    require(f"{layer} 画布 108dp", node.get(A + "width") == "108dp")
    paths = node.findall("path")
    require(f"{layer} 含多条路径", len(paths) >= 3)
    require(f"{layer} 路径均有 pathData", all(p.get(A + "pathData") for p in paths))

# 5) 各密度方形 + 圆形 PNG 齐备
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for density, size in DENSITIES.items():
    for fname in ("ic_launcher.png", "ic_launcher_round.png"):
        p = res / f"mipmap-{density}" / fname
        require(f"mipmap-{density}/{fname} 存在", p.is_file())
        require(f"mipmap-{density}/{fname} 非空", p.stat().st_size > 0)
        # PNG 头里直接读宽高，避免依赖 Pillow
        with p.open("rb") as fh:
            head = fh.read(24)
        require(f"mipmap-{density}/{fname} 是 PNG", head[:8] == b"\x89PNG\r\n\x1a\n")
        width = int.from_bytes(head[16:20], "big")
        height = int.from_bytes(head[20:24], "big")
        require(f"mipmap-{density}/{fname} 为 {size}x{size}（实际 {width}x{height}）",
                width == size and height == size)

# 6) 旧位图图标不再被任何地方引用
java = "\n".join(p.read_text(encoding="utf-8") for p in (root / "app/src/main/java").rglob("*.java"))
require("代码中不再引用旧位图图标", "ic_launcher_grain_yield" not in java)

print("\n全部通过：自适应图标资源链完整")
