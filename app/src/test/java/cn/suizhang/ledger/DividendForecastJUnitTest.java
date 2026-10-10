package cn.suizhang.ledger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Dividend forecast rules, migrated from scripts/DividendForecastTest.java. Expected values are
 * copied unchanged so a failure points at a formula change rather than a rewritten expectation.
 */
public class DividendForecastJUnitTest {

    private static final List<DividendForecast.Report> REPORTS = Arrays.asList(
            new DividendForecast.Report("2023年度", .50),
            new DividendForecast.Report("2024中期", .80),
            new DividendForecast.Report("2024末期", .20),
            new DividendForecast.Report("2025年末", .90),
            new DividendForecast.Report("2026中期（未完成年度）", 5.00),
            new DividendForecast.Report("2020年度", 30.00),
            new DividendForecast.Report("2024异常/负值", -9.00));

    @Test public void oneYearWindowIgnoresUnfinishedYear() {
        DividendForecast.Estimate one = DividendForecast.calculate(REPORTS, 1, 2026);
        assertTrue("A 股近 1 年取最近已完成年度，不计当前未完成年度",
                one.hasData && one.firstYear == 2025 && one.lastYear == 2025);
        assertEquals("近 1 年每份年分红", .90, one.annualPerUnit, 1e-8);
    }

    @Test public void threeYearWindowMergesMultiplePayments() {
        DividendForecast.Estimate three = DividendForecast.calculate(REPORTS, 3, 2026);
        assertEquals("港股多次派息归并到同一报告年度", 3, three.yearsWithReports);
        assertEquals("近 3 年按完整年度总额求平均", .80, three.annualPerUnit, 1e-8);
        assertEquals("输入持股数量后年预计总额", 100.00, DividendForecast.annualTotal(125, three), 1e-8);
    }

    @Test public void fiveYearWindowTreatsMissingYearsAsZero() {
        DividendForecast.Estimate five = DividendForecast.calculate(REPORTS, 5, 2026);
        assertEquals("近 5 年没有披露记录的年份按零计入窗口", .48, five.annualPerUnit, 1e-8);
        assertTrue("年预计总额精度保持可计算",
                Math.abs(DividendForecast.annualTotal(37.5, DividendForecast.calculate(REPORTS, 3, 2026)) - 30.0) < 1e-8);
    }

    @Test public void missingHistoryIsReportedNotZeroed() {
        DividendForecast.Estimate none = DividendForecast.calculate(Collections.emptyList(), 3, 2026);
        assertFalse("缺少历史行返回暂无数据而不是零预测", none.hasData);
        assertEquals("无历史时每份年分红为零", 0d, none.annualPerUnit, 1e-8);
        assertTrue("无历史时年预计总额标记为不可用而不是零",
                Double.isNaN(DividendForecast.annualTotal(100, none)));
        assertTrue("无股数时不展示预测总额",
                Double.isNaN(DividendForecast.annualTotal(0, DividendForecast.calculate(REPORTS, 3, 2026))));
    }

    @Test public void unknownReportYearCannotFabricateHistory() {
        assertFalse("未知报告年度无法伪造历史",
                DividendForecast.calculate(
                        Collections.singletonList(new DividendForecast.Report("未提供期间", 4.0)), 3, 2026).hasData);
    }

    @Test public void onlyOneThreeFiveYearWindows() {
        assertTrue("只接受 1/3/5 年口径", expectInvalidWindow());
    }

    private static boolean expectInvalidWindow() {
        try { DividendForecast.calculate(Collections.emptyList(), 2, 2026); }
        catch (IllegalArgumentException expected) { return true; }
        return false;
    }
}