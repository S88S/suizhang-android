#!/usr/bin/env python3
"""Regression checks for confirmed 2.0 wording, calendar, supplement, and disclosure issues."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
COLORS = {
    "light": ROOT / "app/src/main/res/values/colors.xml",
    "dark": ROOT / "app/src/main/res/values-night/colors.xml",
}

def section(start: str, end: str) -> str:
    if start not in MAIN:
        return ""
    first = MAIN.index(start)
    return MAIN[first:MAIN.index(end, first)]

def require(label: str, condition: bool) -> None:
    if not condition:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)

def palette(path: Path) -> dict[str, str]:
    return {node.attrib["name"]: node.text.strip().upper()
            for node in ET.parse(path).getroot().findall("color")}

def luminance(value: str) -> float:
    rgb = [int(value.lstrip("#")[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in rgb]
    return .2126 * linear[0] + .7152 * linear[1] + .0722 * linear[2]

def contrast(first: str, second: str) -> float:
    high, low = sorted((luminance(first), luminance(second)), reverse=True)
    return (high + .05) / (low + .05)

home_item = section("private void addHomeNextDividend(", "private void addHeroDivider(")
holdings_forecast = section("private void showHoldings() {", "private LinearLayout metric(")
year_month = section("private void addCalendarYearMonth(", "private int countPendingReceived(")
dividend_editor = section("private void showDividendDialog(", "private void showTransactionDialog(")
more = section("private void showMore()", "private LinearLayout moreActionRow(")
more_disclosure = section("private void moreDisclosureSection(", "private void showStatsPage(")

require("总览预期金额使用记录额而非暗示到手的到账额", '"预计记录额 · "' in home_item and '"预计到账 · "' not in home_item)
require("近期收息日期徽章使用容器语义前景色", "R.color.app_on_primary_container" in home_item and "date.setBackground(roundPx(PRIMARY_CONTAINER" in home_item)
require("新增收息行采用现有字号令牌", all(token in home_item for token in ("R.dimen.ds_type_metadata", "R.dimen.ds_type_body", "R.dimen.ds_type_data_value")))

for theme, path in COLORS.items():
    colors = palette(path)
    require(f"{theme} 日期徽章前景对比度达到 4.5:1", contrast(colors["app_on_primary_container"], colors["app_primary_container"]) >= 4.5)
    require(f"{theme} 月格选中描边对选中底色对比度达到 3:1", contrast(colors["app_outline"], colors["app_primary_container"]) >= 3)
    require(f"{theme} Material ripple 对普通表面可辨（3:1）", contrast(colors["app_secondary"], colors["app_surface"]) >= 3)

require("选中月份有独立的可见描边，且 selected 状态与口述说明保留",
        "GradientDrawable monthOutline = roundPx(android.graphics.Color.TRANSPARENT, dimen(R.dimen.ds_calendar_cell_radius), getColor(R.color.app_outline), selected ? dp(1) : 0);" in year_month
        and "ColorStateList.valueOf(getColor(R.color.app_secondary)), monthOutline, roundDp(android.graphics.Color.WHITE, 12, 0, 0)" in year_month
        and "monthCard.setSelected(selected)" in year_month
        and 'selected ? "，已选中"' in year_month)
require("月份未选中态使用高可见度主题 ripple 色", "R.color.app_secondary" in year_month and "R.color.app_primary_container)), null" not in year_month)
require("年度月格拆分币种、到账和预计金额，避免整句在窄卡片内折叠",
        'tokenText(currency, R.dimen.ds_type_metadata' in year_month
        and 'tokenText("已到账 "' in year_month
        and 'tokenText("预计 / 待收 "' in year_month)
require("年度月份空态统一表达为暂无派息记录", "暂无派息记录" in year_month and "暂无收息" not in year_month)
require("年度地图的小字使用字号令牌",
        all(token in year_month for token in ("R.dimen.ds_type_metadata", "R.dimen.ds_type_secondary")))
require("持仓页删除重复年度总计并保留逐持仓分红预估",
        "estimatedIncomeByCurrency(false)" not in holdings_forecast
        and "hasAnyDividendEstimate()" not in holdings_forecast
        and "annualForecast" not in holdings_forecast
        and "Collections.sort(ranked" in holdings_forecast
        and 'item.addView(text("预计年分红  "' in holdings_forecast)

require("补录状态隐藏整个实际到账字段且字段仍加入表单",
        "DividendReceivedAmountState.RECEIVED.equals(receivedState.statusForSave()) ? View.VISIBLE : View.GONE" in dividend_editor
        and "actualReceivedField.setVisibility(received ? View.VISIBLE : View.GONE)" in dividend_editor
        and "f.addView(actualReceivedField)" in dividend_editor)
require("清空到账金额需确认；取消恢复状态/金额且保存复用状态值",
        "DividendReceivedAmountState receivedState" in dividend_editor
        and "receivedState.requestStatus(" in dividend_editor
        and "receivedState.cancelExpectedChange();" in dividend_editor
        and 'selectSpinner(status, "已到账");' in dividend_editor
        and "actualReceived.setText(receivedState.actualAmountForSave());" in dividend_editor
        and 'setNegativeButton("取消并保留金额", (dialog, which) -> restoreReceived.run())' in dividend_editor
        and "confirmation.setOnCancelListener(dialog -> restoreReceived.run())" in dividend_editor
        and "receivedState.setActualAmount(s.toString())" in dividend_editor
        and "receivedState.confirmExpectedChange();" in dividend_editor
        and "String state = receivedState.statusForSave();" in dividend_editor
        and "String actualText = receivedState.actualAmountForSave();" in dividend_editor
        and "date.toString(), state, rate, known, actual, note.getText().toString().trim()" in dividend_editor
        and 'actualReceived.setText("");' not in dividend_editor)

require("更多页按四类菜单折叠，不丢失原页面入口", all(token in more for token in (
        'moreDisclosureSection("account_backup", "账户与备份"',
        'moreDisclosureSection("tax_stats", "税务与统计规划"',
        'moreDisclosureSection("securities_transactions", "证券与交易"',
        'moreDisclosureSection("privacy_licenses", "隐私与许可"',
        "showAccountCard()", "showBackupCard()", "showTaxSettingsCard()",
        "showCatalogCard()", "showTransactions()", "showBoundaries()", "showOpenSourceCard()")))
require("折叠菜单默认收起、展开状态有可感知名称和图标",
        "moreSectionExpanded.getOrDefault(sectionId, false)" in more_disclosure
        and "body.setVisibility(expanded ? View.VISIBLE : View.GONE)" in more_disclosure
        and 'toggle.setContentDescription(title + (expanded ? "，已展开，双击收起" : "，已收起，双击展开"))' in more_disclosure
        and "toggle.setMinimumHeight(dimen(R.dimen.ds_touch_target_min))" in more_disclosure
        and "toggle.setRippleColor(ColorStateList.valueOf(getColor(R.color.app_secondary)))" in more_disclosure
        and "R.drawable.ic_expand_more_24" in more_disclosure
        and "R.drawable.ic_chevron_right_24" in more_disclosure)
require("默认收起时延迟构建隐藏内容，展开时再恢复原入口",
        "if (expanded) {" in more_disclosure
        and more_disclosure.index("if (expanded) {") < more_disclosure.index("buildContent.run();"))
require("菜单展开状态在同一会话重绘及 Activity 重建后保留",
        "moreSectionExpanded" in MAIN
        and 'outState.putBoolean("ui_more_section_"' in MAIN
        and 'savedInstanceState.getBoolean("ui_more_section_"' in MAIN)

imports = [line for line in MAIN.splitlines() if line.strip() == "import android.widget.ImageView;"]
require("MainActivity 不再有重复 ImageView import", len(imports) == 1)
print("通过：2.0 确认缺陷、字体/间距、补录、年度图表与折叠菜单静态回归；设备呈现仍需 Android 运行时验收。")
