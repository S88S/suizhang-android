#!/usr/bin/env python3
"""Source-level checks for the 1.4.5 Miuix calendar, gold palette, and accessible responsive actions."""
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

require("版本只递增到 1.4.5 / code 19", "versionName '1.4.5'" in BUILD and re.search(r"versionCode\s+19\b", BUILD) is not None)
require("月历控件仅由同一卡片承载，没有被隐藏或移到第二行独立卡", "calendarControlPanel()" in calendar and "calendarHelpRow()" not in MAIN)
require("Material 单选组始终要求保留一个已选视图", "group.setSingleSelection(true)" in toggle and "group.setSelectionRequired(true)" in toggle and "addOnButtonCheckedListener" in toggle)
require("月历和年度总览均保留并共享可用宽度", 'String[] labels = {"月历", "年度总览"}' in toggle and "new LinearLayout.LayoutParams(0, dimen(R.dimen.ds_touch_target_min), 1)" in toggle)
require("两项切换带有本地图标且保留单选语义", "R.drawable.ic_nav_calendar" in toggle and "R.drawable.ic_calendar_view_month_24" in toggle and "setCheckable(true)" in toggle)
require("说明按钮复用本地 Material 图标且触控目标至少 48dp", "calendarContextButton(\"日历说明\"" in controls and "dimen(R.dimen.ds_touch_target_min)" in context_button)
require("控制卡片只用一层表面与浅金分段轨道，不重复描边", "CardColumn panel = card()" in controls and "group.setBackground(round(getColor(R.color.app_primary_container)" in toggle and "group.setBackground(round(MINT" not in toggle)
require("日历说明仍可展开/收起且有可访问状态名称", "calendarHelpExpanded = !calendarHelpExpanded; render();" in controls and "已展开，点击收起" in controls and "已折叠，点击展开" in controls)
require("信息图标采用本地 VectorDrawable", "R.drawable.ic_info_24" in controls and (ROOT / "app/src/main/res/drawable/ic_info_24.xml").is_file())
scope_text = section("private String accountScopeLabel()", "private String accessibilityPaneTitle()")
require("账户范围仍显示当前全局范围和总览切换入口", 'return "账户范围：" + accountLabel() + "（在总览切换）"' in scope_text and "accountScopeLabel()" in controls)
require("账户范围可自然换行，无 maxLines 或省略号裁切", "scope.setMaxLines" not in controls and "scope.setEllipsize" not in controls and "new LinearLayout.LayoutParams(0, -2, 1)" in controls)
require("日历账户范围仅为说明，不引入独立筛选动作", "scope.setOnClickListener" not in controls and "selectedAccount" not in controls)
require("账户范围有图标、完整文案仍可换行", "R.drawable.ic_account_balance_wallet_24" in controls and "scope.setMaxLines" not in controls and "scope.setEllipsize" not in controls)
require("账户筛选继续沿用总览全局范围", "db.dividends(selectedAccount, ym)" in calendar and "db.dividendsInYear(selectedAccount, year)" in MAIN and 'content.addView(text(accountScopeLabel()' in holdings)
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
for theme, values in colors.items():
    require(f"{theme} 麦金分红强调与涨红跌绿行情色各自独立", values["app_market_up"] != values["app_dividend_emphasis"] and values["app_calendar_received"] == values["app_dividend_emphasis"])
    require(f"{theme} 分红主卡文字单独配置", "app_dividend_on_hero" in values)

print("通过：Miuix 日历/麦金主题、响应式按钮、行情语义与字体许可护栏的源码级回归；未替代 Android 设备目视验收。")
