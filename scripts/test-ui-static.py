#!/usr/bin/env python3
"""Source-level UI invariants; device rendering still requires Android instrumentation."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")

def require(name: str, condition: bool) -> None:
    if not condition:
        raise AssertionError(f"FAIL {name}")
    print(f"PASS {name}")

def section(start_marker: str, end_marker: str) -> str:
    start = source.index(start_marker)
    end = source.index(end_marker, start)
    return source[start:end]

home = section("private void showHome()", "private void addTaxToggle()")
holdings = section("private void showHoldings()", "private LinearLayout metric(")
calendar = section("private void showCalendar()", "private void addDividendRow(")
stats = section("private void showStatsPage()", "private void showStats()")

for name, body in (("总览", home), ("持仓", holdings), ("日历", calendar)):
    require(f"{name}不再放置税前/税后显眼切换", "addTaxToggle" not in body and "setTaxView" not in body and "税前" not in body and "税后" not in body)
require("总览使用单一预计年分红毛额并单独突出金额", 'estimatedIncomeByCurrency(false)' in home and 'tokenText("预计年分红"' in home and 'hasEstimate ? formatAmounts(annual) : "暂无数据"' in home)
require("持仓卡片仅显示预计年分红单一口径", '预计年分红  ' in holdings and 'YoC' in holdings)
require("统计页仍沿用旧税务显示偏好", "addTaxToggle();" in stats and 'tax_display' in source and "saveSetting(\"tax_display\"" in source)

require("日历有月历/年度总览双视图", 'calendarViewToggle()' in calendar and '"月历", "年度总览"' in calendar)
require("年度视图按所选年份和账户读取", 'db.dividendsInYear(selectedAccount, year)' in calendar)
database = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
require("年度查询保留账户筛选并将年份绑定为四位值", 'String.format(java.util.Locale.ROOT, "%04d", year)' in database and 'd.account_id=? ORDER BY d.pay_date' in database)
require("年度总览显示 12 个月明细且可跳转月历", 'for (int monthIndex = 1; monthIndex <= 12; monthIndex++)' in calendar and 'month = monthIndex' in calendar and 'calendarYearView = false' in calendar)
require("年度趋势按币种分别归一化，不合并币种", 'summary.maxMonthlyTotal(currency)' in calendar and '按币种分别统计，不做汇率换算' in calendar)
require("已到账和预计/待收有文字与颜色双重区分", '已到账' in calendar and '预计 / 待收' in calendar and 'app_calendar_received' in calendar and 'app_calendar_estimated' in calendar)
require("日历日期事件使用文字缩写并提供类型图例", 'markerLabels = {"登", "除", "派"}' in calendar and '登 股权登记' in calendar and '除 除权除息' in calendar and '派 派息日' in calendar)
require("月/年导航含可访问名称和边界守卫", 'calendarNavButton("‹", "上一年"' in calendar and 'isSupportedYear(next)' in calendar and '回到今年' in calendar)
require("年度视图提供空年度状态", '本年度暂无收息事件' in calendar and 'summary.hasEvents()' in calendar)

require("总览用右下角 FAB 取代通栏新增按钮", 'FloatingActionButton addHolding' in source and 'Gravity.END | Gravity.BOTTOM' in source and 'actionButton("＋ 新增持仓", true' not in home)
require("FAB 菜单保留手动新增与券商截图识别", '"手动新增持仓", "识别券商截图"' in source and 'showHoldingEditor(null, null)' in source and 'chooseBrokerScreenshot()' in source)
require("首页优先呈现预计年分红与持仓标的列表", 'estimatedIncomeByCurrency(false)' in home and 'sectionHeader("持仓预计年分红"' in home and 'Math.min(4, ranked.size())' in home)
require("持仓分红卡片呈现来源和股息收益信息且未知值不写作零", 'forecastSourceLabel(holding)' in home and '"暂无数据"' in home and 'gross > 0d ? amount(holding.optString("currency"), gross)' in home)
require("首页保留近期分红预期和日历链接", 'sectionHeader("下一笔预期", "分红日历"' in home and 'db.nextDividends(selectedAccount' in home)
require("无持仓状态不重复显示新增按钮", 'emptyInfoCard("还没有持仓"' in home and 'emptyInfoCard("还没有持仓记录"' in holdings)
require("持仓页新增入口为次级按钮且工具入口保留", 'actionButton("新增持仓", false' in holdings and 'actionButton("更多操作", false' in holdings)
tools = section("private void showHoldingsMoreActions()", "private void holdingMenu(")
for label in ("搜索证券", "导入券商截图", "刷新行情"):
    require(f"更多操作保留功能：{label}", f'"{label}"' in tools)
require("持仓卡片把记分红/记交易收纳进更多菜单", '"记分红", "记交易"' in section("private void holdingMenu(", "private void showCalendar()"))
require("日历手工录入使用次级样式", 'actionButton("手工录入", false' in calendar)
accounts = section("private void showAccountCard()", "private void showTaxSettingsCard()")
require("更多页新建账户使用次级样式", 'actionButton("新增账户", false' in accounts)
catalog = section("private void showCatalogCard()", "private void showBackupCard()")
require("更多页证券搜索使用次级样式", 'actionButton("在线搜索证券", false' in catalog)
require("历史数据获取失败与无可用历史记录使用不同空态", '历史分红数据获取失败 · 可手工填写' in source and '无可用分红记录 · 不代表零分红' in source)

button_start = source.index("private MaterialButton gridActionButton(")
button_end = source.index("private LinearLayout actionButtonRow(", button_start)
grid_button = source[button_start:button_end]
require("操作按钮允许两行文字并居中", "setMaxLines(2)" in grid_button and "setGravity(Gravity.CENTER)" in source)
require("辅助网格按钮高度随字体缩放且最小为 56dp", "Math.max(dp(56), twoLineHeight)" in grid_button)
require("操作按钮网格的单个触控目标至少 48dp", "setMinHeight(dp(48))" in source and "setMinimumHeight(dp(48))" in source)
print("通过：源码级 UI 回归完成；不等同于真机布局测试")
