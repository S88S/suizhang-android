package cn.suizhang.ledger;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MarketDataTest {
    private static int passed;
    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError("FAIL " + name);
        passed++; System.out.println("PASS " + name);
    }
    private static void near(String name, double actual, double expected) {
        check(name + " = " + actual, Math.abs(actual - expected) < 1e-8);
    }
    public static void main(String[] args) {
        String a = "{\"success\":true,\"result\":{\"data\":[{\"SECURITY_NAME_ABBR\":\"贵州茅台\",\"REPORT_DATE\":\"2025-12-31 00:00:00\",\"PRETAX_BONUS_RMB\":280.2423,\"EQUITY_RECORD_DATE\":\"2026-06-25 00:00:00\",\"EX_DIVIDEND_DATE\":\"2026-06-26 00:00:00\",\"ASSIGN_PROGRESS\":\"实施分配\"}]}}";
        List<MarketDataClient.Dividend> aRows = MarketDataClient.parseDividends(a, "A股", "600519");
        check("A 股每 10 股金额恰好除以 10", aRows.size() == 1); near("A 股每股人民币分红", aRows.get(0).perShare, 28.02423);
        check("A 股无派息日期时按除息日+1并标约", "2026-06-27".equals(aRows.get(0).payDate) && aRows.get(0).payDateEstimated);
        check("A 股分红币种 CNY", "CNY".equals(aRows.get(0).currency));

        String hk = "{\"result\":{\"data\":[{\"PLAN_EXPLAIN\":\"每股派港币5.3元\",\"ASSIGN_PERIOD\":\"2025末期\",\"RECORD_DATE\":\"2026-05-20 00:00:00\",\"EX_DIVIDEND_DATE\":\"2026-05-15 00:00:00\",\"DIVIDEND_DATE\":\"2026-06-01\"}]}}";
        List<MarketDataClient.Dividend> hkRows = MarketDataClient.parseDividends(hk, "港股", "00700.HK");
        check("港股每股金额不再除以 10", hkRows.size() == 1); near("港股币种文本解析", hkRows.get(0).perShare, 5.3);
        check("港股币种 HKD", "HKD".equals(hkRows.get(0).currency));
        check("港股保留真实派息日", "2026-06-01".equals(hkRows.get(0).payDate) && !hkRows.get(0).payDateEstimated);

        String us = "{\"result\":{\"data\":[{\"PLAN_EXPLAIN\":\"每1股派0.27美元股息\",\"ASSIGN_PERIOD\":\"2026季度分配\",\"BONUS_PAY_DATE\":\"2026-08-13\",\"ASSIGN_PROGRESS\":null}]}}";
        List<MarketDataClient.Dividend> usRows = MarketDataClient.parseDividends(us, "美股", "AAPL");
        check("美股进度为空仍可解析每股数额", usRows.size() == 1); near("美股美元每股分红", usRows.get(0).perShare, .27);
        check("美股币种 USD", "USD".equals(usRows.get(0).currency));

        String raw = "v_sh600519=\"1~贵州茅台~600519~1258.62~1235.58~1239.53~1~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~20260930161458~23.04~1.86~1268.00~1236.05\";";
        String gbk = new String(raw.getBytes(Charset.forName("GBK")), Charset.forName("GBK"));
        MarketDataClient.Security aSecurity = new MarketDataClient.Security("600519", "", "A股", "CNY");
        List<MarketDataClient.Quote> quotes = MarketDataClient.parseQuotes(gbk, Collections.singletonList(aSecurity), 1000L, false);
        check("腾讯行情 GBK 解码后证券名", quotes.size() == 1 && "贵州茅台".equals(quotes.get(0).name));
        near("行情涨跌幅按百分数原值解析，不再乘 100", quotes.get(0).changePercent, 1.86);
        near("行情现价字段索引", quotes.get(0).price, 1258.62);
        check("港股行情代码零填充", "hk00700".equals(MarketDataClient.quoteTicker(new MarketDataClient.Security("00700.HK", "腾讯", "港股", "HKD"))));

        String search = "{\"QuotationCodeTable\":{\"Data\":[{\"Code\":\"600519\",\"Name\":\"贵州茅台\",\"Classify\":\"AStock\"}]}}";
        List<MarketDataClient.Security> searchRows = MarketDataClient.parseSearch(search, "A股");
        check("证券搜索响应字段和市场类别映射", searchRows.size() == 1 && "A股".equals(searchRows.get(0).market));
        String etfSearch = "{\"QuotationCodeTable\":{\"Data\":[{\"Code\":\"159000\",\"Name\":\"示例ETF甲\",\"Classify\":\"Fund\",\"MarketType\":\"2\"}]}}";
        List<MarketDataClient.Security> etfRows = MarketDataClient.parseSearch(etfSearch, "A股");
        check("虚构基金搜索结果不因 A 股默认类别被过滤", etfRows.size() == 1);
        check("名称含 ETF 的基金结果显示为 ETF 类别", "ETF".equals(MarketDataClient.marketFor(etfRows.get(0), "A股")));
        check("输入虚构代码精确命中同代码证券", MarketDataClient.findExactSecurity(etfRows, "159000") == etfRows.get(0));
        check("相似代码候选不冒充精确匹配", MarketDataClient.findExactSecurity(etfRows, "159001") == null);
        check("港股代码后缀可安全匹配公开搜索返回代码", MarketDataClient.normalizeSearchCode("00700.HK").equals(MarketDataClient.normalizeSearchCode("00700")));
        check("虚构 ETF 映射为深圳行情 ticker", "sz159000".equals(MarketDataClient.quoteTicker(new MarketDataClient.Security("159000", "示例ETF甲", "ETF", "CNY"))));
        check("基金/ETF属于当前公开行情可查类别", MarketDataClient.isQuoteMarket("ETF") && MarketDataClient.isQuoteMarket("基金"));
        String etfQuote = "v_sz159000=\"1~示例ETF甲~159000~2.135~2.100~2.120~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~20261006090000~0~1.67~0~0\";";
        List<MarketDataClient.Quote> etfQuotes = MarketDataClient.parseQuotes(etfQuote, Collections.singletonList(new MarketDataClient.Security("159000", "示例ETF甲", "ETF", "CNY")), 2000L, false);
        check("虚构 ETF 行情解析和 CNY 币种", etfQuotes.size() == 1 && Math.abs(etfQuotes.get(0).price - 2.135d) < 1e-8 && "CNY".equals(etfQuotes.get(0).currency));
        check("搜索缓存明确区分在线与旧缓存状态", MarketDataClient.isCacheFresh(100L, 120L, 30L) && !MarketDataClient.isCacheFresh(1L, 100L, 30L));
        String key1 = MarketDataClient.dividendKey("600519", "2025-12-31", "2026-06-25", "2026-06-26", "2026-06-27");
        String key2 = MarketDataClient.dividendKey("600519", "2025-12-31", "2026-06-25", "2026-06-26", "2026-06-27");
        String key3 = MarketDataClient.dividendKey("600519", "2024-12-31", "2025-06-25", "2025-06-26", "2025-06-27");
        String key4 = MarketDataClient.dividendKey("600519", "2025-12-31", "2026-06-25", "2026-06-26", "2026-06-28");
        check("同一公告重复同步键稳定", key1.equals(key2)); check("不同派息期不会错误去重", !key1.equals(key3));
        check("仅更正派息日不会产生重复公告", key1.equals(key4));
        check("同一日的登记/除息/派息类型合并为一张事件卡", "登记日 / 除息日 / 派息日".equals(DividendCalendarRules.kindsForDate("2026-06-26", "2026-06-26", "2026-06-26", "2026-06-26")));
        check("已公告状态可识别", "已公告".equals(DividendCalendarRules.statusLabel("announced", "expected")));
        check("历史推算状态可识别", "历史推算".equals(DividendCalendarRules.statusLabel("historical_estimate", "expected")));
        check("已到账优先于公告/历史来源状态", "已到账".equals(DividendCalendarRules.statusLabel("announced", "received")));
        check("手工事件不会伪称公告", "手工记录".equals(DividendCalendarRules.statusLabel("manual", "expected")));
        check("无日期不会被误标到日历", "".equals(DividendCalendarRules.kindsForDate(null, "not-a-date", "", "2026-10-04")));

        MarketDataClient.CacheChoice<List<String>> fallback = MarketDataClient.chooseCached(null, Arrays.asList("last-known"), 10L, 20L, 5L);
        check("断网刷新失败回退缓存且标记旧数据", fallback.value != null && fallback.stale && "last-known".equals(fallback.value.get(0)));
        MarketDataClient.CacheChoice<List<String>> fresh = MarketDataClient.chooseCached(Arrays.asList("new"), Arrays.asList("old"), 10L, 20L, 5L);
        check("新网络数据优先于缓存", !fresh.stale && "new".equals(fresh.value.get(0)));
        check("搜索失败时旧缓存仍标记为待核对", MarketDataClient.chooseCached(null, Arrays.asList("old"), 10L, 20L, 5L).stale);
        System.out.println("通过：" + passed + " 项公开数据解析/缓存断言");
    }
}
