package cn.suizhang.ledger;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DividendForecastTest {
    private static int assertions;

    public static void main(String[] args) {
        List<DividendForecast.Report> reports = Arrays.asList(
                new DividendForecast.Report("2023年度", .50),
                new DividendForecast.Report("2024中期", .80),
                new DividendForecast.Report("2024末期", .20),
                new DividendForecast.Report("2025年末", .90),
                new DividendForecast.Report("2026中期（未完成年度）", 5.00),
                new DividendForecast.Report("2020年度", 30.00),
                new DividendForecast.Report("2024异常/负值", -9.00));

        DividendForecast.Estimate one = DividendForecast.calculate(reports, 1, 2026);
        check("A 股近 1 年取最近已完成年度，不计当前未完成年度", one.hasData && one.firstYear == 2025 && one.lastYear == 2025);
        near("近 1 年每份年分红", one.annualPerUnit, .90);

        DividendForecast.Estimate three = DividendForecast.calculate(reports, 3, 2026);
        check("港股多次派息归并到同一报告年度", three.yearsWithReports == 3);
        near("近 3 年按完整年度总额求平均", three.annualPerUnit, .80);
        near("输入持股数量后年预计总额", DividendForecast.annualTotal(125, three), 100.00);

        DividendForecast.Estimate five = DividendForecast.calculate(reports, 5, 2026);
        near("近 5 年没有披露记录的年份按零计入窗口", five.annualPerUnit, .48);
        check("年预计总额精度保持可计算", Math.abs(DividendForecast.annualTotal(37.5, three) - 30.0) < 1e-8);

        DividendForecast.Estimate none = DividendForecast.calculate(Collections.emptyList(), 3, 2026);
        check("缺少历史行返回暂无数据而不是零预测", !none.hasData && none.annualPerUnit == 0d);
        check("无历史时年预计总额标记为不可用而不是零", Double.isNaN(DividendForecast.annualTotal(100, none)));
        check("无股数时不展示预测总额", Double.isNaN(DividendForecast.annualTotal(0, three)));
        check("未知报告年度无法伪造历史", !DividendForecast.calculate(
                Collections.singletonList(new DividendForecast.Report("未提供期间", 4.0)), 3, 2026).hasData);
        check("只接受 1/3/5 年口径", expectInvalidWindow());
        System.out.println("通过：" + assertions + " 项分红预测断言");
    }

    private static boolean expectInvalidWindow() {
        try { DividendForecast.calculate(Collections.emptyList(), 2, 2026); }
        catch (IllegalArgumentException expected) { return true; }
        return false;
    }

    private static void check(String name, boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("FAIL " + name);
        System.out.println("PASS  " + name);
    }

    private static void near(String name, double actual, double expected) {
        check(name + " = " + actual, Math.abs(actual - expected) < 1e-8);
    }
}
