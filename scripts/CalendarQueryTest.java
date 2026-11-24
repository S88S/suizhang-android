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
        check("全部账户按派息/登记/除息三个日期半开月前缀范围过滤", all.sql.contains("WHERE ((d.pay_date>=? AND d.pay_date<?) OR (d.record_date>=? AND d.record_date<?) OR (d.ex_date>=? AND d.ex_date<?))"));
        check("全部账户使用六个月份前缀边界绑定参数", count(all.sql, '?') == 6 && Arrays.equals(all.args, new String[]{"2026-10", "2026-11", "2026-10", "2026-11", "2026-10", "2026-11"}));
        check("缺少持仓或账户的分红仍通过 LEFT JOIN 保留", all.sql.contains("LEFT JOIN holdings h") && all.sql.contains("LEFT JOIN accounts a"));
        check("全部账户查询有稳定排序", all.sql.endsWith("ORDER BY d.pay_date,d._id"));
        check("全部账户分支的日期条件后无多余右括号", !all.sql.contains("))) ORDER BY") && all.sql.contains("d.ex_date<?)) ORDER BY d.pay_date"));

        CalendarQuery.MonthQuery one = CalendarQuery.forMonth(17, "2026-10");
        check("单账户过滤追加在日期范围分组之后", one.sql.contains("d.ex_date<?)) AND d.account_id=? ORDER BY"));
        check("单账户分支无多余右括号", !one.sql.contains("))) AND d.account_id=?"));
        check("单账户绑定七个参数且账户值对应", count(one.sql, '?') == 7 && Arrays.equals(one.args, new String[]{"2026-10", "2026-11", "2026-10", "2026-11", "2026-10", "2026-11", "17"}));

        CalendarQuery.MonthQuery nullMonth = CalendarQuery.forMonth(0, null);
        check("空月份安全降级为空日期范围", Arrays.equals(nullMonth.args, new String[]{"", "", "", "", "", ""}) && !nullMonth.sql.contains("null"));
        CalendarQuery.MonthQuery hostileMonth = CalendarQuery.forMonth(0, "2026-10' OR 1=1 --");
        check("注入样式的月份不能形成日期范围", hostileMonth.sql.equals(all.sql) && Arrays.equals(hostileMonth.args, new String[]{"", "", "", "", "", ""}));
        check("闰年二月的月份前缀上界为三月", Arrays.equals(CalendarQuery.forMonth(0, "2024-02").args,
                new String[]{"2024-02", "2024-03", "2024-02", "2024-03", "2024-02", "2024-03"}));
        check("十二月范围跨到次年一月", Arrays.equals(CalendarQuery.forMonth(0, "2026-12").args,
                new String[]{"2026-12", "2027-01", "2026-12", "2027-01", "2026-12", "2027-01"}));
        check("最大支持年份使用完整月份的字典序上界", Arrays.equals(CalendarQuery.forMonth(0, "9999-12").args,
                new String[]{"9999-12", "9999-13", "9999-12", "9999-13", "9999-12", "9999-13"}));
        System.out.println("通过：" + passed + " 项日历查询回归断言");
    }
}
