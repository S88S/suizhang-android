#!/usr/bin/env python3
"""Regression checks for the 2.0.1 selected-tab, calendar-density, and overview refinement."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
DIMENS = {node.attrib["name"]: node.text.strip() for node in ET.parse(ROOT / "app/src/main/res/values/dimens.xml").getroot().findall("dimen")}
DESIGN = (ROOT / "DESIGN.md").read_text(encoding="utf-8")
README = (ROOT / "README.md").read_text(encoding="utf-8")


def section(start: str, end: str) -> str:
    return MAIN[MAIN.index(start):MAIN.index(end, MAIN.index(start))]


def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)


nav = section("private MiuixBottomNavigatorView bottomNav()", "private float navLabelSp()")
nav_style = section("private void applyBottomNavigationSelection(", "private float navLabelSp()") if "private void applyBottomNavigationSelection(" in MAIN else ""
year_view = section("private void showCalendarYear()", "private int countPendingReceived(")
context_button = section("private MaterialButton calendarContextButton(", "private void showCalendarYear()")
home = section("private void showHome()", "private MaterialButton overviewMetricSettingsButton(")

require("新版号递增且标记为2.0.1", "versionName '2.0.1'" in BUILD and re.search(r"versionCode\s+29\b", BUILD) is not None)
require("主导航当前项同时着色图标与文字，并保留未选中对比", "applyBottomNavigationSelection(nav, selectedItem)" in nav and "R.color.app_primary" in nav_style and "R.color.app_on_surface_variant" in nav_style and "setColorFilter" in nav_style and "setTextColor" in nav_style)
require("底栏选中项用粗体与可访问 selected 状态补充颜色", "Typeface.BOLD" in nav_style and "item.setSelected(selected)" in nav_style)
require("底部四个主页面与既有点击回调保持不变", all(token in nav for token in ("R.id.nav_home", "R.id.nav_holdings", "R.id.nav_calendar", "R.id.nav_more", "render();")))
require("年度地图先展示月度现金流网格，再展示年度收息概况", year_view.index('content.addView(text("月度现金流"') < year_view.index("content.addView(monthGrid") < year_view.index("content.addView(overview"))
require("调整顺序后仍保留按币种拆分的到账/预计汇总和批量确认", all(token in year_view for token in ("summary.receivedByCurrency()", "summary.estimatedByCurrency()", "confirmMarkReceivedBatch(rows)")))
require("回到今年控件文字靠近年份但维持48dp触控目标", "setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL)" in context_button and "dimen(R.dimen.ds_touch_target_min)" in context_button and "ds_space_micro" in year_view)
require("总览主卡仅温和压缩垂直留白，不缩小关键指标触控/阅读尺寸", DIMENS.get("ds_hero_inset_vertical") == "12dp" and DIMENS.get("ds_overview_metric_row_min_height") == "48dp" and DIMENS.get("ds_touch_target_min") == "48dp" and "headingParams.bottomMargin = dimen(R.dimen.ds_space_micro)" in home and "content.addView(heroSurface, margin(0, 4, 0, 4));" in home)
require("设计指南说明新的日历层级、主导航选中态和总览密度", all(token in DESIGN for token in ("主导航选中态", "月度现金流地图先于年度收息概况", "总览紧凑度")))
require("README 更新版本号并区分静态方案与应用运行截图", "versionCode 29" in README and "Android 实际运行截图" in README and "虚构示例数据" in README)

print("通过：2.0.1 导航、年度日历信息顺序与总览紧凑度回归；不替代设备渲染验收。")
