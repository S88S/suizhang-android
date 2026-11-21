package cn.suizhang.ledger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Calendar SQL construction, migrated from scripts/CalendarQueryTest.java. The month string is
 * always bound as a parameter, never concatenated; the injection case below pins that down.
 */
public class CalendarQueryJUnitTest {

    private static long count(String text, char needle) {
        return text.chars().filter(c -> c == needle).count();
    }

    @Test public void allAccountsFiltersOnThreeMilestoneDates() {
        CalendarQuery.MonthQuery all = CalendarQuery.forMonth(0, "2026-10");
        assertTrue("全部账户按派息/登记/除息三种日期过滤",
                all.sql.contains("WHERE ((d.pay_date>=? AND d.pay_date<?) OR (d.record_date>=? AND d.record_date<?) OR (d.ex_date>=? AND d.ex_date<?))"));
        assertEquals("全部账户使用六个绑定参数", 6, count(all.sql, '?'));
        assertArrayEquals("全部账户绑定三个半开月份前缀区间", new String[]{"2026-10", "2026-11", "2026-10", "2026-11", "2026-10", "2026-11"}, all.args);
        assertTrue("全部账户查询有稳定排序", all.sql.endsWith("ORDER BY d.pay_date,d._id"));
        assertTrue("全部账户分支的日期条件后无多余右括号",
                !all.sql.contains("))) ORDER BY") && all.sql.contains("d.ex_date<?)) ORDER BY d.pay_date"));
    }

    @Test public void orphanDividendRowsRemainVisible() {
        CalendarQuery.MonthQuery all = CalendarQuery.forMonth(0, "2026-10");
        assertTrue("持仓记录缺失的分红仍留在日历查询中", all.sql.contains("LEFT JOIN holdings h"));
        assertTrue("账户记录缺失的分红仍留在日历查询中", all.sql.contains("LEFT JOIN accounts a"));
    }

    @Test public void singleAccountAppendsFilterAfterGrouping() {
        CalendarQuery.MonthQuery one = CalendarQuery.forMonth(17, "2026-10");
        assertTrue("单账户过滤追加在日期区间分组之后", one.sql.contains("d.ex_date<?)) AND d.account_id=? ORDER BY"));
        assertFalse("单账户分支无多余右括号", one.sql.contains("))) AND d.account_id=?"));
        assertEquals("单账户绑定七个参数", 7, count(one.sql, '?'));
        assertArrayEquals("单账户绑定四个参数且账户值对应",
                new String[]{"2026-10", "2026-11", "2026-10", "2026-11", "2026-10", "2026-11", "17"}, one.args);
    }

    @Test public void nullMonthDegradesToEmptyBinding() {
        CalendarQuery.MonthQuery nullMonth = CalendarQuery.forMonth(0, null);
        assertArrayEquals("空月份安全降级为空范围而不拼入 SQL", new String[]{"", "", "", "", "", ""}, nullMonth.args);
        assertFalse("空月份不把 null 拼入 SQL", nullMonth.sql.contains("null"));
    }

    @Test public void monthContentIsAlwaysBound() {
        CalendarQuery.MonthQuery all = CalendarQuery.forMonth(0, "2026-10");
        CalendarQuery.MonthQuery hostile = CalendarQuery.forMonth(0, "2026-10' OR 1=1 --");
        assertEquals("月份内容始终作为绑定值，不进入 SQL", all.sql, hostile.sql);
        assertArrayEquals("非法月份不形成可匹配区间", new String[]{"", "", "", "", "", ""}, hostile.args);
    }

    @Test public void rangesHandleLeapYearAndYearBoundary() {
        assertArrayEquals("闰年二月的上界为三月一日",
                new String[]{"2024-02", "2024-03", "2024-02", "2024-03", "2024-02", "2024-03"},
                CalendarQuery.forMonth(0, "2024-02").args);
        assertArrayEquals("十二月范围跨到次年一月一日",
                new String[]{"2026-12", "2027-01", "2026-12", "2027-01", "2026-12", "2027-01"},
                CalendarQuery.forMonth(0, "2026-12").args);
        assertArrayEquals("最大支持月份仍有覆盖整月的排序上界",
                new String[]{"9999-12", "9999-13", "9999-12", "9999-13", "9999-12", "9999-13"},
                CalendarQuery.forMonth(0, "9999-12").args);
    }
}
