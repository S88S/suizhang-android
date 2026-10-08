package cn.suizhang.ledger;

import java.util.ArrayList;

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
        StringBuilder sql = new StringBuilder(
                "SELECT d.*,h.name AS holding_name,h.code AS holding_code,h.tax_rate AS holding_tax_rate,h.market AS holding_market,h.tax_mode AS holding_tax_mode,h.quantity AS holding_quantity,a.name AS account_name " +
                "FROM dividends d JOIN holdings h ON h._id=d.holding_id JOIN accounts a ON a._id=d.account_id " +
                "WHERE (substr(d.pay_date,1,7)=? OR substr(COALESCE(d.record_date,''),1,7)=? OR substr(COALESCE(d.ex_date,''),1,7)=?)");
        ArrayList<String> args = new ArrayList<>();
        args.add(safeMonth);
        args.add(safeMonth);
        args.add(safeMonth);
        if (accountId > 0) {
            sql.append(" AND d.account_id=?");
            args.add(String.valueOf(accountId));
        }
        sql.append(" ORDER BY d.pay_date,d._id");
        return new MonthQuery(sql.toString(), args.toArray(new String[0]));
    }
}
