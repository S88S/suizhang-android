#!/usr/bin/env python3
"""Regression checks for the 2.0.2 selected-tab, calendar-density, and overview refinement."""
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
holdings = section("private void showHoldings()", "private LinearLayout metric(")

require("总览现金流信息先展示月均分红估算，再展示年度预估",
        0 <= home.find('"月均预计分红"') < home.find('LocalDate.now().getYear() + " 年预计总额"'))
require("月均金额由年度多币种估算按12缩放并继续按币种格式化",
        "estimatedIncomeByCurrency(false)" in home
        and "scaleMap(annual, 1d / 12d)" in home
        and "LedgerFormats.formatAmounts(scaleMap(annual, 1d / 12d))" in home)
require("月均说明始终标明只是规划参考、不代表固定到账",
        '按年度预估÷12均摊，仅作规划参考，不代表每月固定到账。' in home)
require("无可用估算仍显示未知态，并区分无持仓与持仓暂无估算",
        'annualText = hasEstimate ? LedgerFormats.formatAmounts(annual) : "暂无数据"' in home
        and "添加持仓后显示估算；未知金额不按 ¥0 计入。" in home
        and "持仓分红暂无可用估算；未知金额不会按 ¥0 计入。" in home)
require("持仓长说明默认折叠、状态可读且保留48dp触控目标",
        "valuationNote.setVisibility(View.GONE)" in holdings
        and 'MaterialButton valuationToggle = actionButton("估值与币种说明", false' in holdings
        and "b.setMinimumHeight(dimen(R.dimen.ds_touch_target_min))" in MAIN
        and 'setContentDescription("估值与币种说明，已收起，双击展开")' in holdings
        and "valuationNote.setVisibility(expanded ? View.VISIBLE : View.GONE)" in holdings
        and "valuationToggle.setIconResource(expanded ? R.drawable.ic_expand_more_24 : R.drawable.ic_chevron_right_24)" in holdings
        and 'setContentDescription("估值与币种说明，已" + (expanded ? "展开，双击收起" : "收起，双击展开"))' in holdings)
require("新版号递增且标记为2.0.2", "versionName '2.0.2'" in BUILD and re.search(r"versionCode\s+30\b", BUILD) is not None)
require("主导航当前项同时着色图标与文字，并保留未选中对比", "applyBottomNavigationSelection(nav, selectedItem)" in nav and "R.color.app_primary" in nav_style and "R.color.app_on_surface_variant" in nav_style and "setColorFilter" in nav_style and "setTextColor" in nav_style)
require("底栏选中项用粗体与可访问 selected 状态补充颜色", "Typeface.BOLD" in nav_style and "item.setSelected(selected)" in nav_style)
require("底部四个主页面与既有点击回调保持不变", all(token in nav for token in ("R.id.nav_home", "R.id.nav_holdings", "R.id.nav_calendar", "R.id.nav_more", "render();")))
require("年度地图先展示月度现金流网格，再展示年度收息概况", year_view.index('content.addView(text("月度现金流"') < year_view.index("content.addView(monthGrid") < year_view.index("content.addView(overview"))
require("调整顺序后仍保留按币种拆分的到账/预计汇总和批量确认", all(token in year_view for token in ("summary.receivedByCurrency()", "summary.estimatedByCurrency()", "confirmMarkReceivedBatch(rows)")))
require("回到今年控件贴近年份且维持48dp触控目标", "currentParams.topMargin = 0" in year_view and "setPaddingRelative(dp(4), 0, dp(4), 0)" in context_button and "dimen(R.dimen.ds_touch_target_min)" in context_button)
require("总览主卡仅温和压缩垂直留白，不缩小关键指标触控/阅读尺寸", DIMENS.get("ds_hero_inset_vertical") == "12dp" and DIMENS.get("ds_overview_metric_row_min_height") == "48dp" and DIMENS.get("ds_touch_target_min") == "48dp" and "headingParams.bottomMargin = dimen(R.dimen.ds_space_micro)" in home and "content.addView(heroSurface, margin(0, 4, 0, 4));" in home)
require("设计指南说明月均优先、持仓说明折叠、日历层级和主导航选中态", all(token in DESIGN for token in ("月均预计分红", "估值与币种说明", "月度现金流地图先于年度收息概况", "主导航选中态")))
require("README 更新版本号并区分静态方案与应用运行截图", "versionCode 30" in README and "Android 实际运行截图" in README and "虚构示例数据" in README)

print("通过：2.0.2 导航、日历、总览优先级和持仓说明披露源码回归；不替代设备渲染验收。")
