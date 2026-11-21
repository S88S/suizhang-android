package cn.suizhang.ledger;

import java.util.ArrayList;
import java.time.DateTimeException;
import java.time.YearMonth;

/** Parameterized month query used by the dividend calendar. Kept UI/SQLite-runtime neutral for regression tests. */
final class CalendarQuery {
    private CalendarQuery() {}

    static final class MonthQuery {
        final String sql;
        final String[] args;

        MonthQuery(String sql, String[] args) {
            this.sql = sql;
            this.args = args;
        }
    }

    static MonthQuery forMonth(long accountId, String month) {
        String safeMonth = month == null ? "" : month.trim();
        String[] range = monthRange(safeMonth);
        StringBuilder sql = new StringBuilder(
                "SELECT d.*,h.name AS holding_name,h.code AS holding_code,h.tax_rate AS holding_tax_rate,h.market AS holding_market,h.tax_mode AS holding_tax_mode,h.quantity AS holding_quantity,a.name AS account_name " +
                // LEFT JOIN keeps orphan dividend rows visible; bounded ranges keep indexed date
                // columns unwrapped, unlike substr-based month matching.
                "FROM dividends d LEFT JOIN holdings h ON h._id=d.holding_id LEFT JOIN accounts a ON a._id=d.account_id " +
                "WHERE ((d.pay_date>=? AND d.pay_date<?) OR (d.record_date>=? AND d.record_date<?) OR (d.ex_date>=? AND d.ex_date<?))");
        ArrayList<String> args = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            args.add(range[0]);
            args.add(range[1]);
        }
        if (accountId > 0) {
            sql.append(" AND d.account_id=?");
            args.add(String.valueOf(accountId));
        }
        sql.append(" ORDER BY d.pay_date,d._id");
        return new MonthQuery(sql.toString(), args.toArray(new String[0]));
    }

    /** ISO month ranges preserve calendar semantics while leaving indexed date columns unwrapped. */
    private static String[] monthRange(String month) {
        try {
            YearMonth selected = YearMonth.parse(month);
            if (selected.getYear() < 1 || selected.getYear() > 9999 || !selected.toString().equals(month)) {
                return new String[]{"", ""};
            }
            String start = selected.toString();
            // Year 10000 has a wider textual representation, so keep the upper bound
            // lexicographically after every valid date in the supported year 9999.
            String end = selected.getYear() == 9999 && selected.getMonthValue() == 12
                    ? "9999-13" : selected.plusMonths(1).toString();
            return new String[]{start, end};
        } catch (DateTimeException invalidMonth) {
            return new String[]{"", ""};
        }
    }
}
