#!/usr/bin/env python3
"""Source-level checks for the selected 2.0 top-tab redesign; device rendering is separate."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN_PATH = ROOT / "app/src/main/java/cn/suizhang/ledger/MainActivity.java"
MAIN = MAIN_PATH.read_text(encoding="utf-8")
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
README = (ROOT / "README.md").read_text(encoding="utf-8")
DESIGN = (ROOT / "DESIGN.md").read_text(encoding="utf-8")


def section(start: str, end: str) -> str:
    return MAIN[MAIN.index(start):MAIN.index(end, MAIN.index(start))]


def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)


home = section("private void showHome()", "private MaterialButton overviewMetricSettingsButton(")
home_event = section("private void addHomeNextDividend(", "private void addHeroDivider(") if "private void addHomeNextDividend(" in MAIN else ""
holdings = section("private void showHoldings()", "private LinearLayout metric(")
year_view = section("private void showCalendarYear()", "private int countPendingReceived(")
calendar = section("private void showCalendar()", "private CardColumn calendarControlPanel()")
more = section("private void showMore()", "private LinearLayout moreActionRow(")

forecast = home.find('LocalDate.now().getYear() + " 年预计分红"')
next_cashflow = home.find('sectionHeader("接下来收息"')
major_holdings = home.find('sectionHeader("主要分红来源"')
require("总览顺序为年度预估、近期收息、主要分红来源", forecast >= 0 and next_cashflow > forecast and major_holdings > next_cashflow)
require("总览近期事件使用真实派息记录并紧凑呈现日期、标的与金额", "db.nextDividends(selectedAccount" in home and "addHomeNextDividend" in home and all(token in home_event for token in ("pay_date", "holding_name", "currency", "total")))
require("近期收息标的不被不可恢复地单行截断", "name.setMaxLines(1)" not in home_event and "name.setEllipsize" not in home_event)
require("近期收息金额不设固定行数上限或省略", "value.setMaxLines(" not in home_event and "value.setEllipsize" not in home_event)

holdings_actions = holdings.find('actionButton("新增持仓"')
holdings_loop = holdings.find("for (JSONObject h : ranked)")
require("持仓页移除重复年度总计并保留逐持仓预计分红", "estimatedIncomeByCurrency(false)" not in holdings and "hasAnyDividendEstimate()" not in holdings and "annualForecast" not in holdings and "预计年分红" in holdings and 'item.addView(text("预计年分红  "' in holdings)
require("持仓页保留账户范围、原操作及按年分红排序的持仓", "accountScopeLabel()" in holdings and holdings_actions >= 0 and holdings_loop > holdings_actions and "Collections.sort(ranked" in holdings and "annualOf(b, false)" in holdings and "ranked.add(holding)" in holdings)
require("持仓页保留本机真实数据、涨红跌绿、原指标与每项编辑/更多操作", all(token in holdings for token in ("db.holdings(selectedAccount)", "marketValueByCurrency(hs)", "MARKET_UP", "MARKET_DOWN", "annualOf(h, false)", "预计年分红", 'actionButton("编辑"', 'actionButton("更多"')))

require("日历默认显示年度现金流地图且配置恢复时保持默认", "private boolean calendarYearView = true;" in MAIN and 'getBoolean("ui_calendar_year_view", true)' in MAIN)
require("年度地图使用响应式三列月格，覆盖十二个月", "monthGrid.setColumnCount(3)" in year_view and 'for (int monthIndex = 1; monthIndex <= 12; monthIndex++)' in year_view)
require("选择月份留在年度地图并展示该月派息事件", "db.dividends(selectedAccount, ym)" in year_view and 'pay_date").startsWith(ym)' in year_view and "calendarYearView = true" in year_view and "calendarMonthSpeech" in year_view)
require("年度地图仍保留切换到逐日月历的路径", 'String[] labels = {"月历", "年度总览"}' in MAIN and "db.dividends(selectedAccount, ym)" in calendar and all(token in calendar for token in ("record_date", "ex_date", "pay_date")))
require("年度地图仍区分已到账和预计金额且按币种统计", "summary.receivedByCurrency()" in year_view and "summary.estimatedByCurrency()" in year_view and "calendarMonthSpeech" in year_view)

require("更多页以本机账本状态开场", all(token in more for token in ("本地优先", "账本数据保存在本机")))
require("更多页统计与规划通过紧凑入口保留原功能", all(token in more for token in ("moreActionRow(", 'activeTab = "stats"', 'activeTab = "plan"')))
require("更多页账户、税务、证券搜索、Excel备份及许可/流水入口保留", all(token in more for token in ("showAccountCard()", "showTaxSettingsCard()", "showCatalogCard()", "showBackupCard()", "showOpenSourceCard()", "showTransactions()", "showBoundaries()")))
require("更多页分组涵盖账户/备份、税务/规划、证券/交易与隐私/许可", all(token in more for token in ("账户与备份", "税务与统计规划", "证券与交易", "隐私与许可")))

require("Debug 安装包升至 versionCode 29", "versionName '2.0.1'" in BUILD and re.search(r"versionCode\s+29\b", BUILD) is not None)
require("README 同步标注 versionCode 29 且设计文档更新总览顺序", "versionCode 29" in README and '"下一笔预期"' not in DESIGN[DESIGN.index('"home_reading_order"'):DESIGN.index('"holding_metrics"')])

print("通过：选定方向的主要 Tab 层级与版本回归；不替代设备视觉验收。")
