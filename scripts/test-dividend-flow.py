#!/usr/bin/env python3
"""Source-level flow regression for selection -> forecast -> persistence -> re-open.
This supplements pure calculation tests; it does not claim Android UI/device execution.
"""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
activity = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
database = (root / "app/src/main/java/cn/suizhang/ledger/LedgerDatabase.java").read_text(encoding="utf-8")
market = (root / "app/src/main/java/cn/suizhang/ledger/MarketDataClient.java").read_text(encoding="utf-8")
def require(label: str, condition: bool) -> None:
    if not condition: raise AssertionError(f"FAIL {label}")
    print(f"PASS  {label}")
def section(source: str, start: str, end: str) -> str:
    return source[source.index(start):source.index(end, source.index(start))]
editor = section(activity, "private void showHoldingEditor(", "private static final class ForecastUiState")
forecast = section(activity, "private void loadDividendForecast(", "private boolean validForecastCode(")
search = section(activity, "private void showSecurityResults(", "private boolean isFundCategory(")
holdings = section(activity, "private void showHoldings()", "private LinearLayout metric(")
home = section(activity, "private void showHome()", "private void addTaxToggle()")
require("选定证券后以回填代码/市场触发自动预测", "requestForecast[0].run()" in editor and 'code.setText(pre.optString("code"))' in editor)
require("在线选择保留代码、市场与币种", all(x in search for x in ('pre.put("code", s.code)', 'pre.put("market", selectedMarket)', 'pre.put("currency"')))
require("手输代码在离焦/防抖后触发，避免逐字符请求", "postDelayed(debouncedForecast" in editor and "code.setOnFocusChangeListener" in editor)
require("默认近 3 年并提供 1/3/5 年按钮", "int[] spans = {1, 3, 5}" in editor and "int years = 3" in activity)
require("预测请求按市场和代码调用公开 A/H 数据客户端", "marketData.dividends(market, code)" in forecast and '"A股".equals(market) || "港股".equals(market)' in forecast)
require("取数失败或无历史显示明确提示且保留手工录入", "result.warning" in forecast and "可手工填写" in forecast)
require("网络慢响应不会覆盖用户请求中的手工输入", "if (state.manualEdited)" in forecast and "保留你刚输入的手工值" in forecast)
require("美股明确标记自动分红暂未支持", "美股自动分红暂不支持" in forecast and 'if (!("A股".equals(market) || "港股".equals(market)))' in forecast)
require("ETF/基金无公开分红源时不伪装零分红", "当前已验证的公开分红接口不支持基金/ETF" in forecast and "不把缺少数据当作零分红" in forecast)
require("每份预测与持股数量变化联动年总额", "DividendForecast.annualTotal(shares, state.estimate)" in editor and "quantity.addTextChangedListener" in editor)
require("A/H 公告和历史推算在日历使用不同数据状态", 'String dataClass = future ? "announced" : "historical_estimate"' in activity)
require("保存 annual_dividend_per_unit 并在现有设置表保存来源", "db.saveHolding(" in editor and "saveForecastMeta(holdingId" in editor and 'db.saveSetting("dividend_forecast_" + holdingId' in activity)
require("保存时仍有请求的成功结果可按原代码补写并刷新主页/持仓", "state.savedHoldingId > 0" in forecast and "db.updateEstimatedAnnualDividend" in forecast and '"home".equals(activeTab) || "holdings".equals(activeTab)' in forecast)
require("编辑重开时从 SQLite 持仓行与元数据恢复预测/来源", 'existing.optDouble("annual_dividend_per_unit")' in editor and "forecastMeta(existing.optLong(\"_id\"))" in editor)
require("持仓卡片不把零预测伪装成有效金额", 'gross > 0d ? amount(h.optString("currency"), gross) : "暂无数据"' in holdings)
require("卡片区分历史推算/手工值并展示更新时间", "forecastSourceLabel(h)" in holdings and 'timeLabel(meta.optLong("updated_at"' in activity)
require("总览无任何预测时显示暂无数据", "hasAnyDividendEstimate()" in home and '"暂无数据"' in home)
require("数据库补写受持仓代码和市场约束且不改变 schema", '"_id=? AND code=? AND market=?"' in database and "DB_VERSION = 3" in database)
require("旧缓存状态可在预测卡片标注", "result.stale" in forecast and '"stale"' in activity)
require("分红客户端捕获代码构造/网络异常并回退缓存", "request(dividendUrl(normalizedMarket, normalizedCode), false)" in market and "fallback.isEmpty()" in market)
print("通过：分红录入/持久化源码级闭环回归（不等同于 UI 真机点击）")
