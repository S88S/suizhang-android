package cn.suizhang.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * LedgerFormats 的行为契约。
 *
 * 这些断言在抽取前无法存在——原方法都是 MainActivity 的私有实例方法，
 * 只能靠脚本 grep 源码证明"存在"，不能证明"算得对"。抽出后第一次可以钉住行为。
 *
 * 期望值全部来自抽取前 MainActivity 的实际实现，不是重新设计的规则：
 * 任何一条失败都意味着行为变了，而不是测试该改。
 */
public class LedgerFormatsJUnitTest {

    // ---------- 日期 ----------

    @Test
    public void parseDateAcceptsIsoDate() {
        assertEquals(LocalDate.of(2025, 5, 1), LedgerFormats.parseDate("2025-05-01"));
    }

    @Test
    public void parseDateTrimsWhitespace() {
        assertEquals(LocalDate.of(2025, 5, 1), LedgerFormats.parseDate("  2025-05-01  "));
    }

    @Test
    public void parseDateReturnsNullForNull() {
        assertNull(LedgerFormats.parseDate(null));
    }

    @Test
    public void parseDateReturnsNullForEmpty() {
        assertNull(LedgerFormats.parseDate(""));
    }

    @Test
    public void parseDateReturnsNullForGarbage() {
        assertNull(LedgerFormats.parseDate("2025/05/01"));
    }

    @Test
    public void shortDateFormatsMonthAndDay() {
        assertEquals("5月1日", LedgerFormats.shortDate("2025-05-01"));
    }

    @Test
    public void shortDateReturnsInputWhenUnparseable() {
        // 不吞掉用户输入：解析不出就原样回显，不能返回空或 null
        assertEquals("不是日期", LedgerFormats.shortDate("不是日期"));
        assertEquals("", LedgerFormats.shortDate(""));
    }

    // ---------- 数值 ----------

    @Test
    public void plainDropsDecimalForIntegers() {
        assertEquals("100", LedgerFormats.plain(100d));
        assertEquals("0", LedgerFormats.plain(0d));
        assertEquals("-42", LedgerFormats.plain(-42d));
    }

    @Test
    public void plainKeepsDecimals() {
        assertEquals("100.5", LedgerFormats.plain(100.5d));
    }

    @Test
    public void plainIsEmptyForNonFinite() {
        assertEquals("", LedgerFormats.plain(Double.NaN));
        assertEquals("", LedgerFormats.plain(Double.POSITIVE_INFINITY));
    }

    // ---------- 金额与百分比 ----------

    @Test
    public void amountUsesCurrencyPrefix() {
        assertEquals("¥1,234.50", LedgerFormats.amount("CNY", 1234.5d));
        assertEquals("HK$1,234.50", LedgerFormats.amount("HKD", 1234.5d));
        assertEquals("$1,234.50", LedgerFormats.amount("USD", 1234.5d));
    }

    @Test
    public void amountFallsBackToCodeWithSpace() {
        assertEquals("JPY 1,234.50", LedgerFormats.amount("JPY", 1234.5d));
    }

    @Test
    public void amountWithoutCurrencyHasNoPrefix() {
        assertEquals("1,234.50", LedgerFormats.amount(null, 1234.5d));
        assertEquals("1,234.50", LedgerFormats.amount("", 1234.5d));
    }

    @Test
    public void amountRendersDashForNonFinite() {
        assertEquals("¥—", LedgerFormats.amount("CNY", Double.NaN));
    }

    @Test
    public void amountRoundsHalfUp() {
        // DecimalFormat 默认 HALF_EVEN，这里必须是 HALF_UP：0.005 -> 0.01 而非 0.00
        assertEquals("¥0.01", LedgerFormats.amount("CNY", 0.005d));
    }

    @Test
    public void percentScalesByHundred() {
        assertEquals("12.50%", LedgerFormats.percent(0.125d));
        assertEquals("100.00%", LedgerFormats.percent(1d));
    }

    @Test
    public void percentRendersDashForNonFinite() {
        assertEquals("—", LedgerFormats.percent(Double.NaN));
    }

    // ---------- 多币种拼接 ----------

    @Test
    public void formatAmountsJoinsWithSeparator() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("CNY", 100d);
        m.put("USD", 20d);
        assertEquals("¥100.00  /  $20.00", LedgerFormats.formatAmounts(m));
    }

    @Test
    public void formatAmountsIsDashWhenEmpty() {
        assertEquals("—", LedgerFormats.formatAmounts(Collections.emptyMap()));
    }

    @Test
    public void formatPercentMapJoinsWithCurrency() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("CNY", 0.1d);
        assertEquals("CNY 10.00%", LedgerFormats.formatPercentMap(m));
    }

    @Test
    public void formatPercentMapIsDashWhenEmpty() {
        assertEquals("—", LedgerFormats.formatPercentMap(Collections.emptyMap()));
    }

    // ---------- 成本法映射 ----------

    @Test
    public void costMethodLabelMapsAllThree() {
        assertEquals("分红摊薄", LedgerFormats.costMethodLabel(FinanceMath.COST_DIVIDEND_ADJUSTED));
        assertEquals("摊薄成本", LedgerFormats.costMethodLabel(FinanceMath.COST_DILUTED));
        assertEquals("加权平均", LedgerFormats.costMethodLabel(FinanceMath.COST_WEIGHTED_AVERAGE));
    }

    @Test
    public void costMethodLabelFallsBackForUnknown() {
        assertEquals("加权平均", LedgerFormats.costMethodLabel("unknown_method"));
        assertEquals("加权平均", LedgerFormats.costMethodLabel(null));
    }

    @Test
    public void costMethodValueRoundTripsLabel() {
        for (String c : new String[]{FinanceMath.COST_DIVIDEND_ADJUSTED,
                FinanceMath.COST_DILUTED, FinanceMath.COST_WEIGHTED_AVERAGE}) {
            assertEquals(c, LedgerFormats.costMethodValue(LedgerFormats.costMethodLabel(c)));
        }
    }

    // ---------- 分币种成本收益率 ----------

    @Test
    public void costYieldKeepsOnlySharedCurrencies() {
        Map<String, Double> annual = new TreeMap<>();
        annual.put("CNY", 8d);
        Map<String, Double> costs = new TreeMap<>();
        costs.put("CNY", 100d);
        costs.put("USD", 50d);   // annual 里没有 USD，必须被跳过
        Map<String, Double> r = LedgerFormats.costYieldByCurrency(annual, costs);
        assertEquals(1, r.size());
        assertTrue(r.containsKey("CNY"));
        assertEquals(0.08d, r.get("CNY"), 1e-9);
    }

    @Test
    public void costYieldIsEmptyWhenNoOverlap() {
        Map<String, Double> annual = new TreeMap<>();
        annual.put("CNY", 8d);
        Map<String, Double> costs = new TreeMap<>();
        costs.put("USD", 50d);
        assertTrue(LedgerFormats.costYieldByCurrency(annual, costs).isEmpty());
    }
}
