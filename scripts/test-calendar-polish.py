#!/usr/bin/env python3
"""Source-level checks for the 1.4.6 Miuix calendar, gold palette, and accessible responsive actions."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
NOTICES = (ROOT / "app/src/main/assets/licenses/THIRD_PARTY_NOTICES.txt").read_text(encoding="utf-8")
SOURCE_NOTICES = (ROOT / "THIRD_PARTY_NOTICES.md").read_text(encoding="utf-8")


def section(start: str, end: str) -> str:
    return MAIN[MAIN.index(start):MAIN.index(end, MAIN.index(start))]


def require(label: str, ok: bool) -> None:
    if not ok:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)


calendar = section("private void showCalendar()", "private void showCalendarYear()")
controls = section("private CardColumn calendarControlPanel()", "private MaterialButton calendarNavButton(")
toggle = section("private MaterialButtonToggleGroup calendarViewToggle()", "private MaterialButton calendarNavButton(")
context_button = section("private MaterialButton calendarContextButton(", "private void showCalendarYear()")
holdings = section("private void showHoldings()", "private LinearLayout metric(")
editor = section("private void showHoldingEditor(", "private interface CatalogPickCallback")
transactions = section("private void showTransactions()", "private void showBoundaries()")
quick_actions = section("private void showHoldingQuickActions()", "private void addTaxToggle()")

require("版本递增到 1.4.7 / code 21", "versionName '1.4.7'" in BUILD and re.search(r"versionCode\s+21\b", BUILD) is not None)
require("日历切换控件独占同一卡片，不再显示说明或账户范围行", "calendarControlPanel()" in calendar and "calendarHelpRow()" not in MAIN and "日历说明" not in MAIN and "accountScopeLabel()" not in controls)
require("Material 单选组始终要求保留一个已选视图", "group.setSingleSelection(true)" in toggle and "group.setSelectionRequired(true)" in toggle and "addOnButtonCheckedListener" in toggle)
require("月历和年度总览均保留并共享可用宽度", 'String[] labels = {"月历", "年度总览"}' in toggle and "new LinearLayout.LayoutParams(0, dimen(R.dimen.ds_touch_target_min), 1)" in toggle)
require("两项切换带有本地图标且保留单选语义", "R.drawable.ic_nav_calendar" in toggle and "R.drawable.ic_calendar_view_month_24" in toggle and "setCheckable(true)" in toggle)
require("控制卡片只用一层表面与浅金分段轨道，不重复描边", "CardColumn panel = card()" in controls and "group.setBackground(roundPx(getColor(R.color.app_primary_container)" in toggle and "group.setBackground(roundDp(MINT" not in toggle)
require("分段选中态圆角按轨道半径扣除内边距，避免底角错位", "Math.max(0, dimen(R.dimen.ds_control_group_radius) - dp(4))" in toggle)
require("资源圆角按px使用、dp圆角只转换一次", "setCornerRadius(radiusPx)" in MAIN and "setCornerRadius(dp(radiusDp))" in MAIN and "roundPx(PRIMARY_CONTAINER, dimen(R.dimen.ds_market_badge_radius)" in MAIN)
require("正文与紧凑元信息采用统一行距令牌", "setLineSpacing(dimen(size <= 11 ? R.dimen.ds_line_spacing_compact : R.dimen.ds_line_spacing_body), 1f)" in MAIN)
home = section("private void showHome()", "private MaterialButton overviewMetricSettingsButton(")
require("首页先日期后标题且使用8dp节奏", home.index("TextView homeDate") < home.index("TextView homeTitle") and "dimen(R.dimen.ds_home_heading_gap)" in home)
miuix_dimensions = (ROOT / "app/src/main/res/values/dimens.xml").read_text(encoding="utf-8")
require("HiMiuix 行高、内距和基础圆角按组件资源覆盖", all(value in miuix_dimensions for value in ("<dimen name=\"miuix_basic_margin\">12dp</dimen>", "<dimen name=\"miuix_basic_padding\">16dp</dimen>", "<dimen name=\"miuix_basic_min_height\">53dp</dimen>", "<dimen name=\"miuix_basic_radius\">15dp</dimen>")))
require("日历说明折叠状态及独立账户范围行已移除", "calendarHelpExpanded" not in MAIN and "ui_calendar_help_expanded" not in MAIN and "日历说明" not in MAIN and "ic_account_balance_wallet_24" not in controls)
require("回到今天/今年保留至少 48dp 触控目标和可访问名称", "dimen(R.dimen.ds_touch_target_min)" in context_button and "setContentDescription(description)" in context_button)
require("月份/年份箭头与回到当前使用相同的轻量透明底样式", "android.graphics.Color.TRANSPARENT" in section("private MaterialButton calendarNavButton(", "private void showCalendarYear()") and "android.graphics.Color.TRANSPARENT" in context_button and "dp(18)" in section("private MaterialButton calendarNavButton(", "private void showCalendarYear()"))
require("已移除的日历说明图标不再绑定到切换卡", "R.drawable.ic_info_24" not in controls)
scope_text = section("private String accountScopeLabel()", "private String accessibilityPaneTitle()")
require("账户范围提示保留在持仓页，日历控件组不再重复显示", 'return "账户范围：" + accountLabel() + "（在总览切换）"' in scope_text and "accountScopeLabel()" not in controls and 'content.addView(text(accountScopeLabel()' in holdings)
require("账户筛选继续由总览全局范围控制月历和年度查询", "db.dividends(selectedAccount, ym)" in calendar and "db.dividendsInYear(selectedAccount, year)" in MAIN)
require("公开同步和手工录入入口保留完整读屏名称及本地图标", 'actionButton("同步公开分红"' in calendar and 'actionButton("手工录入"' in calendar and 'setContentDescription("更新公开分红数据")' in calendar and "ic_refresh_24" in calendar and "ic_edit_note_24" in calendar)
require("加号新增持仓仍可手动新增和识别截图", 'setItems(new String[]{"手动新增持仓", "识别券商截图"}' in quick_actions and "chooseBrokerScreenshot()" in quick_actions)
require("行情有涨红、跌绿、平盘中性和方向文字", 'changePercent > 0d ? MARKET_UP : changePercent < 0d ? MARKET_DOWN : INK' in MAIN and '"上涨 +"' in MAIN and '"下跌 "' in MAIN and '"平盘 0.00%"' in MAIN)
require("持仓与编辑器行情都调用方向文案/颜色", "marketMoveLabel(quote.changePercent)" in holdings and "marketMoveColor(quote.changePercent)" in holdings and "marketMoveLabel(quote.changePercent)" in editor and "setTextColor(marketMoveColor(quote.changePercent))" in editor)
require("买入/卖出标签不再冒充行情涨跌", 'item.addView(text(t.optString("side"), 12, INK, true)' in transactions)
require("分红预测和到账指标使用独立分红语义令牌", "R.color.app_dividend_on_hero" in MAIN and "DIVIDEND" in MAIN)
require("主卡片与卡片列均使用 HiMiuix MiuixCardView", "MiuixCardView heroSurface" in MAIN and "CardColumn extends MiuixCardView" in MAIN)
require("日历使用三色圆点和完整名称图例，不再重复登除派缩写", "calendarMarkerDot(markerColors[ti], 6, selected)" in calendar and 'calendarLegendItem("股权登记"' in calendar and 'calendarLegendItem("除权除息"' in calendar and 'calendarLegendItem("派息日"' in calendar and "markerLabels" not in calendar)
require("日历操作行按屏幕宽度/字体缩放改高度或纵向排列", "fontScale > 1.45f" in calendar and "screenWidthDp < 360" in calendar and "maxTextSp" in calendar and "setMaxLines(2)" in calendar)
require("税前/税后按钮在窄屏或大字体下可重排", "boolean stacked = fontScale > 1.25f" in MAIN and "container.setOrientation(LinearLayout.VERTICAL)" in MAIN)
require("Noto Sans SC 字体在本地加载并携带完整 OFL 文本", "R.font.noto_sans_sc" in MAIN and "NotoSansSC-OFL.txt" in NOTICES and (ROOT / "app/src/main/res/font/noto_sans_sc_regular.otf").is_file() and (ROOT / "app/src/main/assets/licenses/NotoSansSC-OFL.txt").is_file())
require("HiMiuix 日夜主题色由 App 麦金令牌覆盖", "miuix_theme_color" in (ROOT / "app/src/main/res/values/colors.xml").read_text() and "miuix_theme_color" in (ROOT / "app/src/main/res/values-night/colors.xml").read_text())
require("反射读取的 HiMiuix 麦金主题色受资源保留规则保护", 'tools:keep="@color/miuix_*"' in (ROOT / "app/src/main/res/raw/keep.xml").read_text())
require("日历事件仍使用独立登记/除息/派息状态角色色", all(token in calendar for token in ("app_calendar_record_date", "app_calendar_ex_date", "app_calendar_pay_date")) and 'app_calendar_received' in MAIN and 'app_calendar_estimated' in MAIN)
require("上游 Material Icons 提交与许可记录可离线查阅", "af0ed9c0e1276bad43c4d6ca8e8aaa283e425195" in NOTICES and "licenses/MaterialIcons-LICENSE.txt" in NOTICES and (ROOT / "app/src/main/assets/licenses/MaterialIcons-LICENSE.txt").is_file())
require("源码发行包也包含 Material Icons Apache-2.0 完整文本", "third_party/licenses/MaterialIcons-LICENSE.txt" in SOURCE_NOTICES and (ROOT / "third_party/licenses/MaterialIcons-LICENSE.txt").is_file())

light = ET.parse(ROOT / "app/src/main/res/values/colors.xml").getroot()
dark = ET.parse(ROOT / "app/src/main/res/values-night/colors.xml").getroot()
colors = {"light": {e.attrib["name"]: e.text.strip().upper() for e in light.findall("color")}, "dark": {e.attrib["name"]: e.text.strip().upper() for e in dark.findall("color")}}
dimensions = {e.attrib["name"]: e.text.strip() for e in ET.parse(ROOT / "app/src/main/res/values/dimens.xml").getroot().findall("dimen")}
require("月历分段轨道12dp圆角与4dp内距形成8dp选中圆角", dimensions.get("ds_control_group_radius") == "12dp" and "Math.max(0, dimen(R.dimen.ds_control_group_radius) - dp(4))" in toggle)
for theme, values in colors.items():
    require(f"{theme} 麦金分红强调与涨红跌绿行情色各自独立", values["app_market_up"] != values["app_dividend_emphasis"] and values["app_calendar_received"] == values["app_dividend_emphasis"])
    require(f"{theme} 分红主卡文字单独配置", "app_dividend_on_hero" in values)

print("通过：Miuix 日历/麦金主题、响应式按钮、行情语义与字体许可护栏的源码级回归；未替代 Android 设备目视验收。")
