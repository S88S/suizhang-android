package cn.suizhang.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DividendTaxRules 的税率判定契约。
 *
 * 这些规则原本埋在 MainActivity 里，只能靠 grep 确认某一行还在，不能确认判定对不对。
 * 抽出后用 Mock Port 覆盖各市场分支：真人手工记税、A 股三档、港股通、美股协定。
 *
 * 期望值取自抽取前的实际行为，不是重新设计的规则。
 */
public class DividendTaxRulesJUnitTest {

    /** 可编程的账本替身。默认：设置表为空、无批次、批次完整。 */
    private static final class MockPort implements DividendTaxRules.Port {
        final Map<String, String> settings = new HashMap<>();
        List<DividendTaxRules.RawLot> lots = new ArrayList<>();
        boolean coverageComplete = true;
        boolean coverageCalled;
        Boolean lastIncludeToday;
        String lastLotsDate;

        @Override public String setting(String key, String fallback) {
            String v = settings.get(key);
            return v == null ? fallback : v;
        }
        @Override public List<DividendTaxRules.RawLot> taxLotsAt(long holdingId, String date) {
            lastLotsDate = date;
            return lots;
        }
        @Override public boolean hasCompleteTaxLotCoverage(long holdingId, String date,
                                                           double qty, boolean includeToday) {
            coverageCalled = true;
            lastIncludeToday = includeToday;
            return coverageComplete;
        }
    }

    private static final String TODAY = LocalDate.now().toString();

    // ---------- 手工模式 ----------

    @Test
    public void manualModeIsAlwaysKnown() {
        MockPort p = new MockPort();
        assertTrue(DividendTaxRules.estimateKnown(p, "A股", "manual", 1L, 100d, TODAY));
        assertTrue(DividendTaxRules.estimateKnown(p, "美股", "manual", 1L, 100d, TODAY));
        // 手工模式不看设置表，也不去查批次
        assertFalse(p.coverageCalled);
    }

    @Test
    public void manualRateIsClamped() {
        MockPort p = new MockPort();
        assertEquals(0.2d, DividendTaxRules.effectiveRate(p, "A股", "manual", "0.2", 1L, TODAY), 1e-9);
        assertEquals(1d, DividendTaxRules.effectiveRate(p, "A股", "manual", "5", 1L, TODAY), 1e-9);
        assertEquals(0d, DividendTaxRules.effectiveRate(p, "A股", "manual", "-3", 1L, TODAY), 1e-9);
    }

    // ---------- A 股 ----------

    @Test
    public void aShareRequiresMainlandIdentity() {
        MockPort p = new MockPort();
        // 默认 tax_identity 是"未确认"，不是中国内地个人 -> 不可算
        assertFalse(DividendTaxRules.estimateKnown(p, "A股", "auto", 1L, 100d, TODAY));
        assertFalse(p.coverageCalled);

        p.settings.put("tax_identity", "中国内地个人");
        assertTrue(DividendTaxRules.estimateKnown(p, "A股", "auto", 1L, 100d, TODAY));
        assertTrue(p.coverageCalled);
    }

    @Test
    public void aShareRejectsMalformedDate() {
        MockPort p = new MockPort();
        p.settings.put("tax_identity", "中国内地个人");
        // 2025-5-1 能被 parse，但不是规范串，原实现视为不可用
        assertFalse(DividendTaxRules.estimateKnown(p, "A股", "auto", 1L, 100d, "2025-5-1"));
        assertFalse(DividendTaxRules.estimateKnown(p, "A股", "auto", 1L, 100d, "2025/05/01"));
        assertTrue(DividendTaxRules.estimateKnown(p, "A股", "auto", 1L, 100d, "2025-05-01"));
    }

    @Test
    public void aSharePassesTodayFlagOnlyWhenDateIsToday() {
        MockPort p = new MockPort();
        p.settings.put("tax_identity", "中国内地个人");
        DividendTaxRules.estimateKnown(p, "A股", "auto", 1L, 100d, TODAY);
        assertEquals(Boolean.TRUE, p.lastIncludeToday);

        p.lastIncludeToday = null;
        DividendTaxRules.estimateKnown(p, "A股", "auto", 1L, 100d, "2025-05-01");
        assertEquals(Boolean.FALSE, p.lastIncludeToday);
    }

    @Test
    public void aShareRateUsesThreeBands() {
        MockPort p = new MockPort();
        p.settings.put("tax_identity", "中国内地个人");
        p.settings.put("tax_a_short", "0.2");
        p.settings.put("tax_a_mid", "0.1");
        p.settings.put("tax_a_long", "0");
        // 一周前买入 -> 短期档 20%
        LocalDate recent = LocalDate.now().minusDays(7);
        p.lots = new ArrayList<>();
        p.lots.add(new DividendTaxRules.RawLot(100d, recent.toString()));
        double rate = DividendTaxRules.effectiveRate(p, "A股", "auto", null, 1L, TODAY);
        assertEquals(0.2d, rate, 1e-9);
    }

    @Test
    public void aShareRateIsZeroWhenLotDateUnparseable() {
        MockPort p = new MockPort();
        p.settings.put("tax_identity", "中国内地个人");
        p.lots = new ArrayList<>();
        p.lots.add(new DividendTaxRules.RawLot(100d, "not-a-date"));
        // 原实现：任一批次日期坏 -> 整体 0，不是跳过该批次
        assertEquals(0d, DividendTaxRules.effectiveRate(p, "A股", "auto", null, 1L, TODAY), 1e-9);
    }

    @Test
    public void aShareRateIsZeroWhenLotIsNull() {
        MockPort p = new MockPort();
        p.settings.put("tax_identity", "中国内地个人");
        p.lots = new ArrayList<>();
        p.lots.add(null);
        assertEquals(0d, DividendTaxRules.effectiveRate(p, "A股", "auto", null, 1L, TODAY), 1e-9);
    }

    @Test
    public void aShareClampsOutOfRangeSettingsBeforeUse() {
        // 手工改过的 Excel 设置表可能把 tax_a_short 写成 5
        MockPort p = new MockPort();
        p.settings.put("tax_identity", "中国内地个人");
        p.settings.put("tax_a_short", "5");
        p.settings.put("tax_a_mid", "5");
        p.settings.put("tax_a_long", "5");
        p.lots = new ArrayList<>();
        p.lots.add(new DividendTaxRules.RawLot(100d, LocalDate.now().minusDays(7).toString()));
        double rate = DividendTaxRules.effectiveRate(p, "A股", "auto", null, 1L, TODAY);
        // 钳到 1.0 后三档相同，结果应为 1.0 而不是抛异常
        assertEquals(1.0d, rate, 1e-9);
    }

    // ---------- 港股 ----------

    @Test
    public void hkIsKnownOnlyForStockConnectChannel() {
        MockPort p = new MockPort();
        assertFalse(DividendTaxRules.estimateKnown(p, "港股", "auto", 1L, 100d, TODAY));
        p.settings.put("tax_hk_channel", "港股通");
        assertTrue(DividendTaxRules.estimateKnown(p, "港股", "auto", 1L, 100d, TODAY));
    }

    @Test
    public void hkRateIsTwentyPercentForStockConnect() {
        MockPort p = new MockPort();
        assertEquals(0d, DividendTaxRules.effectiveRate(p, "港股", "auto", null, 1L, TODAY), 1e-9);
        p.settings.put("tax_hk_channel", "港股通");
        assertEquals(0.2d, DividendTaxRules.effectiveRate(p, "港股", "auto", null, 1L, TODAY), 1e-9);
    }

    // ---------- 美股 ----------

    @Test
    public void usIsUnknownWhenStatusUnset() {
        MockPort p = new MockPort();
        assertFalse(DividendTaxRules.estimateKnown(p, "美股", "auto", 1L, 100d, TODAY));
    }

    @Test
    public void usTreatyRateIsTenPercent() {
        MockPort p = new MockPort();
        p.settings.put("tax_us_status", "符合协定");
        assertTrue(DividendTaxRules.estimateKnown(p, "美股", "auto", 1L, 100d, TODAY));
        assertEquals(0.10d, DividendTaxRules.effectiveRate(p, "美股", "auto", null, 1L, TODAY), 1e-9);
    }

    @Test
    public void usNoTreatyRateIsThirtyPercent() {
        MockPort p = new MockPort();
        p.settings.put("tax_us_status", "不适用协定");
        assertEquals(0.30d, DividendTaxRules.effectiveRate(p, "美股", "auto", null, 1L, TODAY), 1e-9);
    }

    @Test
    public void usOtherStatusIsZeroRateButKnown() {
        MockPort p = new MockPort();
        p.settings.put("tax_us_status", "已确认不涉及");
        assertTrue(DividendTaxRules.estimateKnown(p, "美股", "auto", 1L, 100d, TODAY));
        assertEquals(0d, DividendTaxRules.effectiveRate(p, "美股", "auto", null, 1L, TODAY), 1e-9);
    }

    // ---------- 未知市场 ----------

    @Test
    public void unknownMarketIsNeverKnown() {
        MockPort p = new MockPort();
        assertFalse(DividendTaxRules.estimateKnown(p, "基金", "auto", 1L, 100d, TODAY));
        assertFalse(DividendTaxRules.estimateKnown(p, null, "auto", 1L, 100d, TODAY));
        assertEquals(0d, DividendTaxRules.effectiveRate(p, "基金", "auto", null, 1L, TODAY), 1e-9);
    }

    // ---------- 钳制 ----------

    @Test
    public void clampRateKeepsWithinUnitInterval() {
        assertEquals(0d, DividendTaxRules.clampRate(-1d), 1e-9);
        assertEquals(1d, DividendTaxRules.clampRate(2d), 1e-9);
        assertEquals(0.25d, DividendTaxRules.clampRate(0.25d), 1e-9);
    }

    @Test
    public void aShareFallsBackToDefaultWhenSettingIsNotANumber() {
        // 手工改坏设置表：tax_a_short="abc" 必须回退 0.2，不能变成 NaN
        // NaN 会绕过 clampRate 并让 aShareRecordDateTaxRate 抛 IllegalArgumentException
        MockPort p = new MockPort();
        p.settings.put("tax_identity", "中国内地个人");
        p.settings.put("tax_a_short", "abc");
        p.settings.put("tax_a_mid", "def");
        p.settings.put("tax_a_long", "ghi");
        p.lots = new ArrayList<>();
        p.lots.add(new DividendTaxRules.RawLot(100d, LocalDate.now().minusDays(7).toString()));
        double rate = DividendTaxRules.effectiveRate(p, "A股", "auto", null, 1L, TODAY);
        assertEquals(0.2d, rate, 1e-9);
    }

    @Test
    public void settingDoubleFallsBackOnMissingAndInvalid() {
        MockPort p = new MockPort();
        assertEquals(0.2d, DividendTaxRules.settingDouble(p, "absent", 0.2), 1e-9);
        p.settings.put("broken", "not-a-number");
        assertEquals(0.1d, DividendTaxRules.settingDouble(p, "broken", 0.1), 1e-9);
        p.settings.put("good", "0.35");
        assertEquals(0.35d, DividendTaxRules.settingDouble(p, "good", 0.1), 1e-9);
    }
}
