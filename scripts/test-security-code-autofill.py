#!/usr/bin/env python3
"""Source-level acceptance checks for code auto-fill without touching ledger data."""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/cn/suizhang/ledger/MainActivity.java").read_text(encoding="utf-8")
client = (root / "app/src/main/java/cn/suizhang/ledger/MarketDataClient.java").read_text(encoding="utf-8")
build = (root / "app/build.gradle").read_text(encoding="utf-8")
def check(label, condition):
    if not condition: raise AssertionError("FAIL " + label)
    print("PASS " + label)
lookup = main[main.index("private void requestSecurityCodeLookup("):main.index("private static final class ForecastUiState")]
check("代码查询防抖 650ms", "postDelayed(pendingCodeLookup[0], 650L)" in main)
check("异步搜索与行情均校验请求序号和当前代码", lookup.count("requestId != sequence[0]") >= 2 and lookup.count("normalizeSearchCode(code.getText().toString())") >= 2)
check("复用现有公开证券搜索，不把市场类别当过滤器", "marketData.search(requestedCode, fallbackMarket)" in lookup and "findExactSecurity(snapshot.rows, requestedCode)" in lookup)
check("必须精确代码匹配才自动回填身份", "if (found == null)" in lookup and "没有精确代码匹配" in lookup)
check("自动回填名称、市场/类别和币种", "name.setText(found.name)" in lookup and "marketFor(found, fallbackMarket)" in lookup and "defaultCurrency(effectiveMarket, found.code)" in lookup)
check("回填可获取的公开行情并显示来源、更新时间和缓存状态", "marketData.quotes(Collections.singletonList(quoteSecurity))" in lookup and "腾讯公开行情" in lookup and "quoteCache" in lookup and "timeLabel(quote.updatedAt)" in lookup)
check("无精确结果/接口失败仍允许继续手工录入", "不自动填数量/成本，可继续手工填写或稍后重试" in lookup and "公开证券查询暂不可用" in lookup)
check("代码回填函数不改数量、成本或交易/到账记录", "quantity.setText" not in lookup and "cost.setText" not in lookup and "db.addTransaction" not in lookup and "db.addDividend" not in lookup)
check("ETF/基金可查询行情并按现有公开缓存标记", '"ETF".equals(market)' in client and '"基金".equals(market)' in client and "isCacheFresh" in client)
check("基金/ETF无已验证分红源时不伪装零分红", "当前已验证的公开分红接口不支持基金/ETF" in main and "不把缺少数据当作零分红" in main)
check("版本递增一次至 1.4.0 / versionCode 14", "versionName '1.4.0'" in build and "versionCode 14" in build)
print("通过：证券代码自动回填源码与隐私回归")
