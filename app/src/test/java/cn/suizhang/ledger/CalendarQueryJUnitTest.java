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
                all.sql.contains("WHERE (substr(d.pay_date,1,7)=? OR substr(COALESCE(d.record_date,''),1,7)=? OR substr(COALESCE(d.ex_date,''),1,7)=?)"));
        assertEquals("全部账户使用三个绑定参数", 3, count(all.sql, '?'));
        assertArrayEquals("全部账户绑定同一月份三次", new String[]{"2026-10", "2026-10", "2026-10"}, all.args);
        assertTrue("全部账户查询有稳定排序", all.sql.endsWith("ORDER BY d.pay_date,d._id"));
        assertTrue("全部账户分支的日期条件后无多余右括号",
                !all.sql.contains(")) ORDER BY") && all.sql.contains("=?) ORDER BY d.pay_date"));
    }

    @Test public void singleAccountAppendsFilterAfterGrouping() {
        CalendarQuery.MonthQuery one = CalendarQuery.forMonth(17, "2026-10");
        assertTrue("单账户过滤追加在括号条件之后", one.sql.contains("?) AND d.account_id=? ORDER BY"));
        assertFalse("单账户分支无多余右括号", one.sql.contains(")) AND d.account_id=?"));
        assertEquals("单账户绑定四个参数", 4, count(one.sql, '?'));
        assertArrayEquals("单账户绑定四个参数且账户值对应",
                new String[]{"2026-10", "2026-10", "2026-10", "17"}, one.args);
    }

    @Test public void nullMonthDegradesToEmptyBinding() {
        CalendarQuery.MonthQuery nullMonth = CalendarQuery.forMonth(0, null);
        assertArrayEquals("空月份安全降级为空过滤值而不拼入 SQL", new String[]{"", "", ""}, nullMonth.args);
        assertFalse("空月份不把 null 拼入 SQL", nullMonth.sql.contains("null"));
    }

    @Test public void monthContentIsAlwaysBound() {
        CalendarQuery.MonthQuery all = CalendarQuery.forMonth(0, "2026-10");
        CalendarQuery.MonthQuery hostile = CalendarQuery.forMonth(0, "2026-10' OR 1=1 --");
        assertEquals("月份内容始终作为绑定值，不进入 SQL", all.sql, hostile.sql);
        assertTrue("注入样式的月份只出现在绑定值中", hostile.args[0].contains("OR 1=1"));
    }
}