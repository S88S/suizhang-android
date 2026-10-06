package cn.suizhang.ledger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

public final class CalendarQueryTest {
    private static int passed;

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError("FAIL " + name);
        passed++;
        System.out.println("PASS " + name);
    }

    private static long count(String text, char needle) {
        return text.chars().filter(c -> c == needle).count();
    }

    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void emit(String name, CalendarQuery.MonthQuery query) {
        System.out.println("QUERY\t" + name + "\t" + b64(query.sql) + "\t" + b64(String.join("\n", query.args)));
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--emit".equals(args[0])) {
            emit("ALL", CalendarQuery.forMonth(0, "2026-10"));
            emit("ACCOUNT_1", CalendarQuery.forMonth(1, "2026-10"));
            emit("ACCOUNT_2", CalendarQuery.forMonth(2, "2026-10"));
            return;
        }

        CalendarQuery.MonthQuery all = CalendarQuery.forMonth(0, "2026-10");
        check("全部账户按派息/登记/除息三种日期过滤", all.sql.contains("WHERE (substr(d.pay_date,1,7)=? OR substr(COALESCE(d.record_date,''),1,7)=? OR substr(COALESCE(d.ex_date,''),1,7)=?)"));
        check("全部账户使用三个绑定参数", count(all.sql, '?') == 3 && Arrays.equals(all.args, new String[]{"2026-10", "2026-10", "2026-10"}));
        check("全部账户查询有稳定排序", all.sql.endsWith("ORDER BY d.pay_date,d._id"));
        check("全部账户分支的日期条件后无多余右括号", !all.sql.contains(")) ORDER BY") && all.sql.contains("=?) ORDER BY d.pay_date"));

        CalendarQuery.MonthQuery one = CalendarQuery.forMonth(17, "2026-10");
        check("单账户过滤追加在括号条件之后", one.sql.contains("?) AND d.account_id=? ORDER BY"));
        check("单账户分支无多余右括号", !one.sql.contains(")) AND d.account_id=?"));
        check("单账户绑定四个参数且账户值对应", count(one.sql, '?') == 4 && Arrays.equals(one.args, new String[]{"2026-10", "2026-10", "2026-10", "17"}));

        CalendarQuery.MonthQuery nullMonth = CalendarQuery.forMonth(0, null);
        check("空月份安全降级为空过滤值而不拼入 SQL", Arrays.equals(nullMonth.args, new String[]{"", "", ""}) && !nullMonth.sql.contains("null"));
        CalendarQuery.MonthQuery hostileMonth = CalendarQuery.forMonth(0, "2026-10' OR 1=1 --");
        check("月份内容始终作为绑定值，不进入 SQL", hostileMonth.sql.equals(all.sql) && hostileMonth.args[0].contains("OR 1=1"));
        System.out.println("通过：" + passed + " 项日历查询回归断言");
    }
}
