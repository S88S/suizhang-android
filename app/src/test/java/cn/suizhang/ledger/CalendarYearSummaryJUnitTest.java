package cn.suizhang.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Year-view aggregation, migrated from scripts/CalendarYearSummaryTest.java. Currencies are kept
 * separate on purpose: the app never converts between them, and these assertions pin that down.
 */
public class CalendarYearSummaryJUnitTest {

    @Test public void supportedYearRange() {
        assertTrue("年度视图允许 1 至 9999 的显示年份",
                CalendarYearSummary.isSupportedYear(1) && CalendarYearSummary.isSupportedYear(9999));
        assertFalse("年度导航拒绝零年和 10000 年溢出",
                CalendarYearSummary.isSupportedYear(0) || CalendarYearSummary.isSupportedYear(10000));
    }

    @Test public void expectedAndReceivedAreTrackedSeparately() {
        CalendarYearSummary summary = CalendarYearSummary.calculate(2026, events());
        assertTrue("非空记录形成年度总览", summary.hasEvents());
        assertEquals("1 月预计与到账分开累计", 40d, summary.month(1).amounts("CNY").received, 1e-8);
        assertEquals("1 月预估金额单独保留", 100d, summary.month(1).amounts("CNY").estimated, 1e-8);
        assertEquals("1 月事件数准确", 2, summary.month(1).eventCount());
        assertEquals("币种互不合并，2 月 HKD 单独统计", 200d, summary.month(2).amounts("HKD").estimated, 1e-8);
        assertEquals("12 月边界事件落入 12 月", 1, summary.month(12).eventCount());
        assertEquals("跨年 1 月事件不混入本年度", 2, summary.month(1).eventCount());
    }

    @Test public void yearlyTotalsNeverMergeCurrencies() {
        CalendarYearSummary summary = CalendarYearSummary.calculate(2026, events());
        assertEquals("年度已到账合计按币种计算", 50d, summary.receivedByCurrency().get("CNY"), 1e-8);
        assertEquals("年度预计合计按币种计算", 100d, summary.estimatedByCurrency().get("CNY"), 1e-8);
        assertTrue("年度预计汇总不把 HKD 并入 CNY",
                summary.estimatedByCurrency().size() == 2 && summary.estimatedByCurrency().containsKey("HKD"));
        assertEquals("月度趋势峰值用于币种内归一化", 140d, summary.maxMonthlyTotal("CNY"), 1e-8);
    }

    @Test public void emptyLedgerAndOutOfRangeYear() {
        CalendarYearSummary empty = CalendarYearSummary.calculate(2026, null);
        assertTrue("空账本年度状态稳定且无事件",
                !empty.hasEvents() && empty.month(1).eventCount() == 0);
        try {
            CalendarYearSummary.calculate(10000, events());
            fail("年度汇总拒绝超界年份");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    /**
     * Mixed-currency fixtures including the cases that must be dropped rather than guessed:
     * a cross-year date, an unparsable date, and negative/NaN amounts.
     */
    private static List<CalendarYearSummary.Event> events() {
        List<CalendarYearSummary.Event> events = new ArrayList<>();
        events.add(new CalendarYearSummary.Event("2026-01-02", "CNY", "expected", 100));
        events.add(new CalendarYearSummary.Event("2026-01-15", "CNY", "received", 40));
        events.add(new CalendarYearSummary.Event("2026-02-01", "HKD", "expected", 200));
        events.add(new CalendarYearSummary.Event("2026-12-31", "CNY", "received", 10));
        events.add(new CalendarYearSummary.Event("2027-01-01", "CNY", "received", 999));
        events.add(new CalendarYearSummary.Event("not-a-date", "CNY", "received", 500));
        events.add(new CalendarYearSummary.Event("2026-03-01", "USD", "expected", -1));
        events.add(new CalendarYearSummary.Event("2026-03-01", "USD", "expected", Double.NaN));
        return events;
    }
}