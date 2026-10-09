#!/usr/bin/env python3
"""Regression checks for the 1.4.5 Miuix calendar hierarchy and wheat-gold UI."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
DESIGN = (ROOT / "DESIGN.md").read_text(encoding="utf-8")


def section(start: str, end: str) -> str:
    return MAIN[MAIN.index(start):MAIN.index(end, MAIN.index(start))]


def require(label: str, ok: bool) -> None:
    if not ok:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)


controls = section("private CardColumn calendarControlPanel()", "private MaterialButton calendarNavButton(")
toggle = section("private MaterialButtonToggleGroup calendarViewToggle()", "private MaterialButton calendarNavButton(")
context_button = section("private MaterialButton calendarContextButton(", "private void showCalendarYear()")
calendar = section("private void showCalendar()", "private CardColumn calendarControlPanel()")
render = section("private void render()", "private com.google.android.material.bottomnavigation.BottomNavigationView bottomNav()")

require("新包版本递增且应用身份可覆盖升级", "versionName '1.4.7'" in BUILD and re.search(r"versionCode\s+21\b", BUILD) is not None and "applicationId 'cn.suizhang.ledger'" in BUILD and "applicationIdSuffix '.debug'" in BUILD)
require("继续使用现有 Material Components Views 依赖", "com.google.android.material:material:1.14.0" in BUILD and "MaterialButtonToggleGroup" in MAIN and "androidx.compose" not in BUILD)
require("麦金色作为浅/深主题主色，日历与行情语义另保留", '"primary"' in DESIGN and "#8A5E0B" in DESIGN and "#F0C45B" in DESIGN and "#B3261E" in DESIGN and "#FF8A80" in DESIGN)
require("日历视图组保持单选并使用图标", "group.setSingleSelection(true)" in toggle and "group.setSelectionRequired(true)" in toggle and "ic_nav_calendar" in toggle and "ic_calendar_view_month_24" in toggle)
require("月历/年度总览等宽且点击高度至少 48dp", 'String[] labels = {"月历", "年度总览"}' in toggle and "new LinearLayout.LayoutParams(0, dimen(R.dimen.ds_touch_target_min), 1)" in toggle)
require("日历控件卡片仅承载月历/年度总览切换", "calendarViewToggle()" in controls and "日历说明" not in MAIN and "accountScopeLabel()" not in controls and "calendarHelpExpanded" not in MAIN)
require("日历卡片采用 HiMiuix 表面且分段轨道为浅金色", "CardColumn panel = card()" in controls and "CardColumn extends MiuixCardView" in MAIN and "group.setBackground(round(getColor(R.color.app_primary_container)" in toggle and "group.setBackground(round(PRIMARY_CONTAINER" not in toggle)
require("分段按钮下沿圆角与浅金轨道内沿相配", "Math.max(0, dimen(R.dimen.ds_control_group_radius) - dp(4))" in toggle)
require("月份/年份箭头与回到当前控件统一为透明轻量样式", "android.graphics.Color.TRANSPARENT" in context_button and "android.graphics.Color.TRANSPARENT" in section("private MaterialButton calendarNavButton(", "private void showCalendarYear()") and "dp(18)" in section("private MaterialButton calendarNavButton(", "private void showCalendarYear()"))
require("账户范围提示仍只在持仓页显示且筛选仍是全局的", "accountScopeLabel()" in MAIN and "accountScopeLabel()" not in controls and "db.dividends(selectedAccount, ym)" in calendar and "db.dividendsInYear(selectedAccount, year)" in MAIN)
require("月份/年份导航与回到当前按钮都使用本地图标", all(icon in MAIN for icon in ("ic_chevron_left_24", "ic_chevron_right_24", "ic_today_24")))
require("分红同步和手工录入保留原回调并配本地图标", "this::syncPublicDividends" in calendar and "this::beginAddDividend" in calendar and "ic_refresh_24" in calendar and "ic_edit_note_24" in calendar)
require("日历以圆点替代登除派缩写，且保留可访问日期/事件描述", "calendarMarkerDot(markerColors[ti], 6, selected)" in calendar and "calendarTypes(types)" in calendar and "markerLabels" not in calendar)
require("Miuix 主卡片、图标字体和本地 OFL 字体许可齐备", "MiuixCardView heroSurface" in MAIN and "CardColumn extends MiuixCardView" in MAIN and "R.font.noto_sans_sc" in MAIN and (ROOT / "app/src/main/assets/licenses/NotoSansSC-OFL.txt").is_file())
require("同步操作行针对大字或窄屏自适应", "fontScale > 1.45f" in calendar and "screenWidthDp < 360" in calendar and "actionHeight" in calendar)
require("日历遵循提醒、月份导航、图例、待收概况、更新时间、日历网格的层级", calendar.index("db.announcedDividends") < calendar.index("LinearLayout monthHead") < calendar.index("本月到账与待收") < calendar.index("公开分红数据上次更新") < calendar.index("GridLayout grid"))
require("公告金额按币种分列，保留官方公告核对提示", "formatAmounts(announcedAmounts)" in calendar and "以正式公告及券商记录为准" in calendar)
require("待收用记录额，已到账优先显示用户录入的实收额", "received_amount" in calendar and "到账优先用实收金额" in calendar)
require("外部圈选只留在设计说明，不实现手绘圆圈", "不复刻成 App 的圈线或装饰边框" in DESIGN and "drawCircle(" not in MAIN and "addCircle(" not in MAIN)
require("日期标记、账户过滤和既有说明口径仍保留", all(token in calendar for token in ("record_date", "ex_date", "pay_date", "db.dividends(selectedAccount, ym)")) and "（在总览切换）" in MAIN)

required_icons = [
    "ic_calendar_view_month_24.xml", "ic_chevron_left_24.xml", "ic_chevron_right_24.xml",
    "ic_today_24.xml", "ic_refresh_24.xml", "ic_edit_note_24.xml", "ic_expand_more_24.xml",
]
for filename in required_icons:
    path = ROOT / "app/src/main/res/drawable" / filename
    require(f"本地矢量图标存在且 XML 有效：{filename}", path.is_file() and ET.parse(path).getroot().tag == "vector")

for theme in ("values/colors.xml", "values-night/colors.xml"):
    nodes = ET.parse(ROOT / "app/src/main/res" / theme).getroot().findall("color")
    colors = {node.attrib["name"]: node.text.strip().lstrip("#") for node in nodes}
    up = colors["app_market_up"]
    down = colors["app_market_down"]
    dividend = colors["app_dividend_emphasis"]
    require(f"{theme} 麦金主色与涨红跌绿市场色分离", colors["app_primary"].upper() == ("8A5E0B" if theme == "values/colors.xml" else "F0C45B") and up != dividend and int(up[0:2], 16) > int(up[2:4], 16) * 1.4 and int(down[2:4], 16) > int(down[0:2], 16) * 1.4)

print("通过：1.4.6 日历层级、麦金主题、Miuix 日历卡片、本地图标和业务交互源码回归；不替代真机屏幕验收。")
