#!/usr/bin/env python3
"""Static regression checks for user-configurable overview metrics."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = (ROOT / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
DB = (ROOT / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
DESIGN = (ROOT / "docs/design/DESIGN.md").read_text(encoding="utf-8")


def require(label: str, ok: bool) -> None:
    if not ok:
        raise AssertionError("FAIL " + label)
    print("PASS " + label)


home = MAIN[MAIN.index("private void showHome()"):MAIN.index("private void addHomeHoldingCard(")]
settings = MAIN[MAIN.index("private void showOverviewMetricSettings()"):MAIN.index("private List<String> selectedHomeMetrics(")]
values = MAIN[MAIN.index("private Map<String, String> homeMetricValues("):MAIN.index("private String overviewHoldingDuration(")]

require("总览采用主题协调的摘要卡并突出年度分红", "MiuixCardView heroSurface" in home and "R.color.app_dividend_on_hero" in home and "年预计分红" in home)
require("首页日期位于主标题上方并保留自然换行", home.index("TextView homeDate") < home.index("TextView homeTitle") and "LinearLayout homeHeading = new LinearLayout(this)" in home)
require("首页日期标题采用固定纵向层级和8dp间距", "homeHeading.setOrientation(LinearLayout.VERTICAL)" in home and "dimen(R.dimen.ds_home_heading_gap)" in home)
require("设计规范与首页层级、启动器图标放大规则一致", "首页日期在主标题上方" in DESIGN and "中央图形放大约15%并保持居中" in DESIGN)
require("无持仓摘要精简说明但继续区分未知金额与零值", "添加持仓后显示估算；未知金额不按 ¥0 计入。" in home and '"暂无数据"' in home)
require("首页指标单元居中并保持48dp行高", "ds_overview_metric_row_min_height" in home and "cell.setGravity(Gravity.CENTER)" in home)
require("通用文字样式遵守设计令牌的11sp元信息字号下限", "ds_type_metadata" in MAIN and "Math.max(size, metadataSize)" in MAIN)
require("默认六项指标、最多六项且候选共十二项", "HOME_METRIC_LIMIT = 6" in MAIN and "HOME_METRIC_DEFAULTS = \"cost_yield,market_yield,floating_profit,net_invested,holding_count,profit_rate\"" in MAIN and "HOME_METRIC_IDS = {" in MAIN and MAIN.split("HOME_METRIC_IDS = {", 1)[1].split("};", 1)[0].count('"') == 24)
require("指标可选 0–6 项，零项隐藏指标栅格", "if (!metricIds.isEmpty())" in home and "可选 0–6 项" in settings)
require("选择保存在本地设置中并可恢复默认", "db.saveSetting(HOME_METRIC_SETTING, android.text.TextUtils.join" in settings and "db.saveSetting(HOME_METRIC_SETTING, HOME_METRIC_DEFAULTS)" in settings)
require("超出六项时即时取消本次勾选", "count > HOME_METRIC_LIMIT" in settings and "setItemChecked(which, false)" in settings)
require("指标覆盖预设财务项及六个补充项", all(f'values.put("{key}"' in values for key in ("cost_yield", "market_yield", "floating_profit", "net_invested", "holding_count", "profit_rate", "received_year", "total_cost", "market_value", "monthly_forecast", "daily_forecast", "cumulative_received")))
require("净投入按完整交易流水汇总并保留账户过滤", "netInvestedByCurrency(selectedAccount)" in values and "WHERE t.account_id=? GROUP BY h.currency" in DB)
require("净投入按含买入手续费的买入额减扣卖出手续费后的卖出净额", "t.quantity*t.price+t.fees" in DB and "-(t.quantity*t.price-t.fees)" in DB)
require("年度到账和累计到账只统计 received 记录", "db.incomeSummary(selectedAccount, targetYear)" in MAIN and '"received".equals(row.optString("status"))' in MAIN and "db.receivedAllTime(selectedAccount)" in MAIN)
require("市值息率和盈亏率只基于已取得的行情", "quote == null || quote.price <= 0d" in MAIN and "profitRateByCurrency(JSONArray holdings)" in MAIN and "if (quote == null) continue" in MAIN)
require("货币金额按币种格式化并保留换汇边界说明", "private String formatAmounts(Map<String, Double> values)" in MAIN and "金额按币种分开展示，不做汇率换算" in settings)
require("最早买入时间按所选范围和实际首买日期计算", 'db.firstPurchaseDate(h.optLong("_id"))' in MAIN and "首笔买入至今" in MAIN)
require("设计说明记录总览可选指标和恢复默认", "用户自选 0–6 项指标" in DESIGN and "恢复默认" in DESIGN)
print("通过：总览指标配置与计算口径静态回归完成；不替代 Android 设备渲染验收。")
