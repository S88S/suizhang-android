package cn.suizhang.ledger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CalendarYearSummaryTest {
    private static int passed;

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError("FAIL " + name);
        passed++;
        System.out.println("PASS " + name);
    }

    private static void near(String name, double actual, double expected) {
        check(name, Math.abs(actual - expected) < 1e-8);
    }

    public static void main(String[] args) {
        check("年度视图允许 1 至 9999 的显示年份", CalendarYearSummary.isSupportedYear(1) && CalendarYearSummary.isSupportedYear(9999));
        check("年度导航拒绝零年和 10000 年溢出", !CalendarYearSummary.isSupportedYear(0) && !CalendarYearSummary.isSupportedYear(10000));

        List<CalendarYearSummary.Event> events = new ArrayList<>();
        events.add(new CalendarYearSummary.Event("2026-01-02", "CNY", "expected", 100));
        events.add(new CalendarYearSummary.Event("2026-01-15", "CNY", "received", 40));
        events.add(new CalendarYearSummary.Event("2026-02-01", "HKD", "expected", 200));
        events.add(new CalendarYearSummary.Event("2026-12-31", "CNY", "received", 10));
        events.add(new CalendarYearSummary.Event("2027-01-01", "CNY", "received", 999));
        events.add(new CalendarYearSummary.Event("not-a-date", "CNY", "received", 500));
        events.add(new CalendarYearSummary.Event("2026-03-01", "USD", "expected", -1));
        events.add(new CalendarYearSummary.Event("2026-03-01", "USD", "expected", Double.NaN));

        CalendarYearSummary summary = CalendarYearSummary.calculate(2026, events);
        check("非空记录形成年度总览", summary.hasEvents());
        near("1 月预计与到账分开累计", summary.month(1).amounts("CNY").received, 40);
        near("1 月预估金额单独保留", summary.month(1).amounts("CNY").estimated, 100);
        check("1 月事件数准确", summary.month(1).eventCount() == 2);
        near("币种互不合并，2 月 HKD 单独统计", summary.month(2).amounts("HKD").estimated, 200);
        check("12 月边界事件落入 12 月", summary.month(12).eventCount() == 1);
        check("跨年 1 月事件不混入本年度", summary.month(1).eventCount() == 2);
        near("年度已到账合计按币种计算", summary.receivedByCurrency().get("CNY"), 50);
        near("年度预计合计按币种计算", summary.estimatedByCurrency().get("CNY"), 100);
        check("年度预计汇总不把 HKD 并入 CNY", summary.estimatedByCurrency().size() == 2 && summary.estimatedByCurrency().containsKey("HKD"));
        near("月度趋势峰值用于币种内归一化", summary.maxMonthlyTotal("CNY"), 140);

        CalendarYearSummary empty = CalendarYearSummary.calculate(2026, null);
        check("空账本年度状态稳定且无事件", !empty.hasEvents() && empty.month(1).eventCount() == 0);
        boolean rejected = false;
        try { CalendarYearSummary.calculate(10000, events); } catch (IllegalArgumentException expected) { rejected = true; }
        check("年度汇总拒绝超界年份", rejected);
        System.out.println("通过：" + passed + " 项年度总览回归断言");
    }
}
