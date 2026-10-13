package cn.suizhang.ledger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class LedgerDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "suizhang-ledger.db";
    // Package-private, not private: ExcelBackup stamps this into every workbook it writes. Keeping
    // one source stops a future schema bump from being silently dropped on import.
    static final int DB_VERSION = 5;

    LedgerDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        // WAL lets the render path read while a write transaction is open. Must be set before the
        // first database is opened, so it belongs here rather than onConfigure().
        setWriteAheadLoggingEnabled(true);
    }

    /**
     * Opening position (initial_quantity, opened_on) per holding. Tax estimation walks the FIFO
     * lots for one holding three separate times and each pass re-read the same holdings row, so a
     * per-render cache removes two thirds of those reads. Cleared on every write; see clearOpeningCache.
     */
    // Concurrent, not HashMap: tax estimation fills this from the single-thread dataExecutor while
    // the UI thread clears it on every write. Concurrent put() on a plain HashMap can corrupt the
    // table or spin forever, which shows up as a random freeze rather than a stack trace.
    private final Map<Long, Opening> openingCache = new ConcurrentHashMap<>();
    private static final class Opening { final double quantity; final String openedOn;
        Opening(double q, String o) { quantity = q; openedOn = o; } }

    /** Returns the opening position for a holding, or null when the row is missing. */
    private Opening openingAt(SQLiteDatabase db, long holdingId) {
        Opening cached = openingCache.get(holdingId);
        if (cached != null) return cached.quantity < 0d ? null : cached;
        try (Cursor c = db.rawQuery("SELECT initial_quantity,opened_on FROM holdings WHERE _id=?", new String[]{String.valueOf(holdingId)})) {
            if (!c.moveToFirst()) { openingCache.put(holdingId, new Opening(-1d, "")); return null; }
            Opening row = new Opening(c.getDouble(0), c.getString(1) == null ? "" : c.getString(1));
            openingCache.put(holdingId, row);
            return row;
        }
    }

    private void clearOpeningCache() { openingCache.clear(); }

    @Override public synchronized void onCreate(SQLiteDatabase db) {
        createCore(db);
        ContentValues initial = new ContentValues(); initial.put("name", "默认账户"); initial.put("created_at", Instant.now().toString());
        db.insert("accounts", null, initial);
        createExtras(db);
        seedTaxDefaults(db);
        seedAssetIndex(db);
    }

    private void createCore(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE holdings (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, code TEXT, market TEXT NOT NULL, currency TEXT NOT NULL, quantity REAL NOT NULL, cost REAL NOT NULL, account_id INTEGER NOT NULL, created_at TEXT NOT NULL, annual_dividend_per_unit REAL NOT NULL DEFAULT 0, tax_rate REAL NOT NULL DEFAULT 0.10, opened_on TEXT NOT NULL DEFAULT '', initial_quantity REAL NOT NULL DEFAULT 0, tax_mode TEXT NOT NULL DEFAULT 'manual', cost_method TEXT NOT NULL DEFAULT 'weighted_average', opening_cost REAL NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE dividends (_id INTEGER PRIMARY KEY AUTOINCREMENT, holding_id INTEGER NOT NULL, account_id INTEGER NOT NULL, amount_per_share REAL NOT NULL, total REAL NOT NULL, currency TEXT NOT NULL, record_date TEXT, ex_date TEXT, pay_date TEXT NOT NULL, status TEXT NOT NULL, note TEXT, created_at TEXT NOT NULL, source TEXT NOT NULL DEFAULT 'manual', source_key TEXT, data_class TEXT NOT NULL DEFAULT 'manual', record_quantity REAL NOT NULL DEFAULT 0, pay_date_estimated INTEGER NOT NULL DEFAULT 0, tax_rate REAL NOT NULL DEFAULT 0, tax_known INTEGER NOT NULL DEFAULT 0, received_amount REAL)");
        db.execSQL("CREATE TABLE transactions (_id INTEGER PRIMARY KEY AUTOINCREMENT, holding_id INTEGER NOT NULL, account_id INTEGER NOT NULL, side TEXT NOT NULL, trade_date TEXT NOT NULL, quantity REAL NOT NULL, price REAL NOT NULL, fees REAL NOT NULL, note TEXT, created_at TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_dividends_paydate ON dividends(pay_date)");
        db.execSQL("CREATE INDEX idx_dividends_recorddate ON dividends(record_date)");
        db.execSQL("CREATE INDEX idx_dividends_exdate ON dividends(ex_date)");
        db.execSQL("CREATE UNIQUE INDEX idx_dividends_source_key ON dividends(source_key) WHERE source_key IS NOT NULL");
        db.execSQL("CREATE INDEX idx_holdings_account ON holdings(account_id)");
        // Every render walks one holding's tax lots three times, and every later
        // transactions/dividends read filtered by holding_id was a full table scan.
        createIndexIfMissing(db, "idx_transactions_holding", "transactions", "holding_id");
        createIndexIfMissing(db, "idx_dividends_holding", "dividends", "holding_id");
    }

    private static void createIndexIfMissing(SQLiteDatabase db, String index, String table, String column) {
        db.execSQL("CREATE INDEX IF NOT EXISTS " + index + " ON " + table + "(" + column + ")");
    }

    @Override public synchronized void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // Indexes added after the schema froze. IF NOT EXISTS lets already-installed databases
        // pick them up here, so adding one never needs a version bump or migration path.
        createIndexIfMissing(db, "idx_transactions_holding", "transactions", "holding_id");
        createIndexIfMissing(db, "idx_dividends_holding", "dividends", "holding_id");
    }

    private void createExtras(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS expense_goals (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, period TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, created_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS asset_index (_id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL UNIQUE, name TEXT NOT NULL, market TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    }

    private void seedTaxDefaults(SQLiteDatabase db) {
        String[][] rows = {
                {"tax_default_a", "0.10"}, {"tax_default_hk", "0.20"},
                {"tax_default_us", "0.10"}, {"tax_default_fund", "0.00"}, {"tax_default_etf", "0.00"},
                {"tax_a_short", "0.20"}, {"tax_a_mid", "0.10"}, {"tax_a_long", "0.00"},
                {"tax_display", "gross"}, {"drip_yield", "0.04"}, {"drip_contribution", "0"},
                {"drip_reinvest", "1"}, {"drip_years", "20"}, {"drip_tax", "0.10"}, {"drip_currency", "CNY"}
        };
        for (String[] row : rows) {
            ContentValues cv = new ContentValues(); cv.put("key", row[0]); cv.put("value", row[1]);
            db.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private void seedAssetIndex(SQLiteDatabase db) {
        String[][] rows = {
                {"600519", "贵州茅台", "A股"}, {"601398", "工商银行", "A股"},
                {"601088", "中国神华", "A股"}, {"000858", "五粮液", "A股"},
                {"600900", "长江电力", "A股"}, {"510300", "沪深300ETF", "ETF"},
                {"510880", "红利ETF", "ETF"}, {"159915", "创业板ETF", "ETF"},
                {"00700.HK", "腾讯控股", "港股"}, {"00941.HK", "中国移动", "港股"},
                {"AAPL", "Apple", "美股"}, {"KO", "Coca-Cola", "美股"},
                {"VOO", "Vanguard S&P 500 ETF", "ETF"}, {"SCHD", "Schwab US Dividend Equity ETF", "ETF"}
        };
        for (String[] row : rows) {
            ContentValues cv = new ContentValues(); cv.put("code", row[0]); cv.put("name", row[1]); cv.put("market", row[2]);
            db.insertWithOnConflict("asset_index", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    @Override public synchronized void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE holdings ADD COLUMN annual_dividend_per_unit REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE holdings ADD COLUMN tax_rate REAL NOT NULL DEFAULT 0.10");
            db.execSQL("UPDATE holdings SET tax_rate=CASE market WHEN 'A股' THEN 0.10 WHEN '港股' THEN 0.20 WHEN '美股' THEN 0.10 ELSE 0.00 END");
            createExtras(db); seedTaxDefaults(db); seedAssetIndex(db);
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE holdings ADD COLUMN opened_on TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE holdings ADD COLUMN initial_quantity REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE holdings ADD COLUMN tax_mode TEXT NOT NULL DEFAULT 'manual'");
            // created_at is a UTC audit timestamp; never truncate it with substr() to get the
            // business date. Use date(...,'localtime') so a record made late at night still lands
            // on the correct local calendar day instead of being pushed back a day before 08:00.
            db.execSQL("UPDATE holdings SET opened_on=date(created_at,'localtime'), initial_quantity=MAX(0, quantity-COALESCE((SELECT SUM(CASE WHEN t.side='买入' THEN t.quantity ELSE -t.quantity END) FROM transactions t WHERE t.holding_id=holdings._id),0))");
            db.execSQL("ALTER TABLE dividends ADD COLUMN source TEXT NOT NULL DEFAULT 'manual'");
            db.execSQL("ALTER TABLE dividends ADD COLUMN source_key TEXT");
            db.execSQL("ALTER TABLE dividends ADD COLUMN data_class TEXT NOT NULL DEFAULT 'manual'");
            db.execSQL("ALTER TABLE dividends ADD COLUMN record_quantity REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE dividends ADD COLUMN pay_date_estimated INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE dividends ADD COLUMN tax_rate REAL NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE dividends ADD COLUMN tax_known INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE dividends SET tax_rate=COALESCE((SELECT tax_rate FROM holdings WHERE holdings._id=dividends.holding_id),0), tax_known=1");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_dividends_recorddate ON dividends(record_date)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_dividends_exdate ON dividends(ex_date)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_dividends_source_key ON dividends(source_key) WHERE source_key IS NOT NULL");
        }
        if (oldVersion < 4) {
            // Existing balances are an untouched opening checkpoint; the selected method applies forward.
            db.execSQL("ALTER TABLE holdings ADD COLUMN cost_method TEXT NOT NULL DEFAULT 'weighted_average'");
            db.execSQL("ALTER TABLE dividends ADD COLUMN received_amount REAL");
        }
        if (oldVersion < 5) {
            // Schema 5 lets a trade or dividend be edited or deleted, which means the stored unit
            // cost can no longer be the only record of how it was reached: it has to be
            // recomputable. opening_cost is the unit cost the replay starts from. It is derived
            // here by walking each holding's history backwards from the cost it already holds, so
            // no existing position changes by a single cent - see CostReplay.recoverOpening.
            db.execSQL("ALTER TABLE holdings ADD COLUMN opening_cost REAL NOT NULL DEFAULT 0");
            backfillOpeningCost(db);
        }
    }

    /**
     * Downgrades are never supported: a newer schema may store data the old one cannot read, so
     * dropping columns would silently corrupt or lose it. Android's default throws an opaque
     * SQLiteException; this makes the cause explicit for whoever wires an older APK to this DB.
     */
    @Override public synchronized void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException(
                "数据库已为 v" + oldVersion + "，无法降级到 v" + newVersion +
                "。请升级应用，或清空数据后重新导入备份。");
    }

    /**
     * Fills opening_cost for every holding from its current numbers.
     *
     * <p>Called from the schema 5 migration, where opening_cost does not exist yet and the only
     * truth available is the incrementally maintained (quantity, cost) pair. A holding whose
     * history cannot be walked back - one that was closed out at some point - lands on an opening
     * of zero, which replays to exactly the numbers it already has.
     */
    private void backfillOpeningCost(SQLiteDatabase db) {
        List<Long> ids = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT _id FROM holdings", null)) {
            while (c.moveToNext()) ids.add(c.getLong(0));
        }
        for (Long id : ids) syncOpeningCost(db, id);
    }

    /**
     * Re-derives opening_cost so that replaying the holding's history lands on the (quantity,
     * cost) it holds right now.
     *
     * <p>Needed whenever something sets the unit cost without going through the replay: the
     * holding editor lets the user type a cost directly, and that typed value has to become the
     * replay's result rather than be overwritten by the next rebuild.
     */
    private void syncOpeningCost(SQLiteDatabase db, long holdingId) {
        if (holdingId <= 0) return;
        double quantity, cost; String method;
        try (Cursor c = db.rawQuery("SELECT quantity,cost,cost_method FROM holdings WHERE _id=?",
                new String[]{String.valueOf(holdingId)})) {
            if (!c.moveToFirst()) return;
            quantity = c.getDouble(0); cost = c.getDouble(1); method = c.getString(2);
        }
        if (method == null || method.isEmpty()) method = FinanceMath.COST_WEIGHTED_AVERAGE;
        CostReplay.State opening = CostReplay.recoverOpening(
                new CostReplay.State(Math.max(0d, quantity), cost), method, replayEvents(db, holdingId));
        ContentValues cv = new ContentValues();
        cv.put("opening_cost", Double.isFinite(opening.cost) ? opening.cost : 0d);
        db.update("holdings", cv, "_id=?", new String[]{String.valueOf(holdingId)});
    }

    /**
     * Recomputes quantity and unit cost by replaying the holding's whole history.
     *
     * <p>This is what makes editing and deleting safe: rather than trying to undo one incremental
     * step, it starts from the opening checkpoint and re-applies everything that is left. Any
     * edit, in any position, ends up consistent by construction.
     */
    private void rebuildHolding(SQLiteDatabase db, long holdingId) {
        if (holdingId <= 0) return;
        double openingQuantity, openingCost; String method;
        try (Cursor c = db.rawQuery("SELECT initial_quantity,opening_cost,cost_method FROM holdings WHERE _id=?",
                new String[]{String.valueOf(holdingId)})) {
            if (!c.moveToFirst()) return;
            openingQuantity = c.getDouble(0); openingCost = c.getDouble(1); method = c.getString(2);
        }
        if (method == null || method.isEmpty()) method = FinanceMath.COST_WEIGHTED_AVERAGE;
        CostReplay.State end = CostReplay.replay(
                new CostReplay.State(Math.max(0d, openingQuantity), openingCost), method, replayEvents(db, holdingId));
        ContentValues cv = new ContentValues();
        cv.put("quantity", Math.max(0d, end.quantity));
        cv.put("cost", Double.isFinite(end.cost) ? end.cost : 0d);
        db.update("holdings", cv, "_id=?", new String[]{String.valueOf(holdingId)});
    }

    /**
     * Every trade and every received dividend for a holding, ordered by date then id.
     *
     * <p>Received dividends are included because the dividend-adjusted basis deducts them from
     * the cost, so a rebuild that only looked at trades would put back basis the dividend removed.
     * Their ordering key is pay_date, the date the money actually arrived; a dividend still only
     * expected has not moved the cost and is left out.
     */
    private List<CostReplay.Event> replayEvents(SQLiteDatabase db, long holdingId) {
        List<CostReplay.Event> events = new ArrayList<>();
        String id = String.valueOf(holdingId);
        try (Cursor c = db.rawQuery("SELECT _id,side,trade_date,quantity,price,fees FROM transactions WHERE holding_id=? ORDER BY trade_date,_id",
                new String[]{id})) {
            while (c.moveToNext()) {
                events.add(new CostReplay.Event(c.getString(2), c.getLong(0), c.getString(1),
                        c.getDouble(3), c.getDouble(4), c.getDouble(5), 0d));
            }
        }
        // received_amount IS NOT NULL, not COALESCE(received_amount,total): markDividendReceived
        // only dilutes the cost when an actual amount was entered, so a received dividend with no
        // amount has never moved the cost and must not start moving it now.
        try (Cursor c = db.rawQuery("SELECT _id,pay_date,received_amount FROM dividends WHERE holding_id=? AND status='received' AND received_amount IS NOT NULL ORDER BY pay_date,_id",
                new String[]{id})) {
            while (c.moveToNext()) {
                double receipt = c.getDouble(2);
                if (!Double.isFinite(receipt) || receipt <= 0d) continue;
                events.add(new CostReplay.Event(c.getString(1), c.getLong(0), "分红", 0d, 0d, 0d, receipt));
            }
        }
        // Trades before dividends on the same day: the money arrives after the day's trading. The
        // order has to be total and stable, because recoverOpening walks the same list backwards
        // and any tie broken differently would recover a different opening cost.
        events.sort((a, b) -> {
            int byDate = a.date == null ? (b.date == null ? 0 : 1) : (b.date == null ? -1 : a.date.compareTo(b.date));
            if (byDate != 0) return byDate;
            int byKind = Integer.compare(dividendRank(a), dividendRank(b));
            return byKind != 0 ? byKind : Long.compare(a.id, b.id);
        });
        return events;
    }

    private static int dividendRank(CostReplay.Event e) { return "分红".equals(e.kind) ? 1 : 0; }

    JSONArray accounts() { return query("SELECT _id,name,created_at FROM accounts ORDER BY _id", null); }
    JSONArray holdings(long accountId) {
        String sql = "SELECT h.*,a.name AS account_name FROM holdings h JOIN accounts a ON a._id=h.account_id";
        if (accountId > 0) return query(sql + " WHERE h.account_id=? ORDER BY h.name COLLATE NOCASE", new String[]{String.valueOf(accountId)});
        return query(sql + " ORDER BY h.name COLLATE NOCASE", null);
    }
    JSONArray dividends(long accountId, String month) {
        CalendarQuery.MonthQuery request = CalendarQuery.forMonth(accountId, month);
        return query(request.sql, request.args);
    }
    JSONArray dividendsInYear(long accountId, int year) {
        String yearValue = String.format(java.util.Locale.ROOT, "%04d", year);
        String sql = "SELECT d.*,h.name AS holding_name,h.code AS holding_code FROM dividends d JOIN holdings h ON h._id=d.holding_id WHERE substr(d.pay_date,1,4)=?";
        if (accountId > 0) return query(sql + " AND d.account_id=? ORDER BY d.pay_date", new String[]{yearValue, String.valueOf(accountId)});
        return query(sql + " ORDER BY d.pay_date", new String[]{yearValue});
    }
    JSONArray incomeSummary(long accountId, int year) {
        // %04d, not String.valueOf: pay_date is matched by substr(...,1,4), so a year below 1000
        // would otherwise compare "999" against "0999" and silently drop the whole year.
        String yearValue = String.format(java.util.Locale.ROOT, "%04d", year);
        String sql = "SELECT d.currency,d.status,SUM(CASE WHEN d.status='received' THEN COALESCE(d.received_amount,d.total) ELSE d.total END) AS total,COUNT(*) AS count FROM dividends d WHERE substr(d.pay_date,1,4)=?";
        if (accountId > 0) return query(sql + " AND d.account_id=? GROUP BY d.currency,d.status ORDER BY d.currency,d.status", new String[]{yearValue, String.valueOf(accountId)});
        return query(sql + " GROUP BY d.currency,d.status ORDER BY d.currency,d.status", new String[]{yearValue});
    }
    JSONArray receivedAllTime(long accountId) {
        String sql = "SELECT currency,SUM(COALESCE(received_amount,total)) AS total,COUNT(*) AS count FROM dividends WHERE status='received'";
        if (accountId > 0) return query(sql + " AND account_id=? GROUP BY currency ORDER BY currency", new String[]{String.valueOf(accountId)});
        return query(sql + " GROUP BY currency ORDER BY currency", null);
    }
    JSONArray nextDividends(long accountId, String today, int limit) {
        String sql = "SELECT d.*,h.name AS holding_name,h.code AS holding_code,h.tax_rate AS holding_tax_rate,h.market AS holding_market,h.tax_mode AS holding_tax_mode,h.quantity AS holding_quantity FROM dividends d JOIN holdings h ON h._id=d.holding_id WHERE d.pay_date>=? AND d.status='expected'";
        if (accountId > 0) return query(sql + " AND d.account_id=? ORDER BY d.pay_date LIMIT " + Math.max(1, limit), new String[]{today, String.valueOf(accountId)});
        return query(sql + " ORDER BY d.pay_date LIMIT " + Math.max(1, limit), new String[]{today});
    }
    JSONArray announcedDividends(long accountId, String today) {
        String sql = "SELECT d.*,h.name AS holding_name,h.code AS holding_code FROM dividends d JOIN holdings h ON h._id=d.holding_id WHERE d.pay_date>=? AND d.status='expected' AND d.data_class='announced'";
        if (accountId > 0) return query(sql + " AND d.account_id=? ORDER BY d.pay_date", new String[]{today, String.valueOf(accountId)});
        return query(sql + " ORDER BY d.pay_date", new String[]{today});
    }
    JSONArray transactions(long accountId, int limit) { return transactions(accountId, limit, 0L); }

    /**
     * The trade list, or exactly one row when {@code onlyId} is given.
     *
     * <p>The single-row form exists for the editor: the list is capped at the most recent trades,
     * so a row that has scrolled off the end would otherwise be impossible to open.
     */
    JSONArray transactions(long accountId, int limit, long onlyId) {
        String sql = "SELECT t.*,h.name AS holding_name,h.code AS holding_code FROM transactions t JOIN holdings h ON h._id=t.holding_id WHERE t._id=?";
        if (onlyId > 0) return query(sql, new String[]{String.valueOf(onlyId)});
        sql = "SELECT t.*,h.name AS holding_name,h.code AS holding_code FROM transactions t JOIN holdings h ON h._id=t.holding_id";
        if (accountId > 0) return query(sql + " WHERE t.account_id=? ORDER BY t.trade_date DESC,t._id DESC LIMIT " + Math.max(1, limit), new String[]{String.valueOf(accountId)});
        return query(sql + " ORDER BY t.trade_date DESC,t._id DESC LIMIT " + Math.max(1, limit), null);
    }

    /** One dividend with the holding fields the editor and the tax estimate both need. */
    JSONObject dividendRow(long id) {
        JSONArray rows = query("SELECT d.*,h.name AS holding_name,h.code AS holding_code,h.market AS holding_market,"
                + "h.tax_mode AS holding_tax_mode,h.tax_rate AS holding_tax_rate,h.quantity AS holding_quantity"
                + " FROM dividends d JOIN holdings h ON h._id=d.holding_id WHERE d._id=?", new String[]{String.valueOf(id)});
        return rows.length() == 0 ? null : rows.optJSONObject(0);
    }
    JSONArray netInvestedByCurrency(long accountId) {
        String sql = "SELECT h.currency AS currency,COALESCE(SUM(CASE WHEN t.side='买入' THEN t.quantity*t.price+t.fees WHEN t.side='卖出' THEN -(t.quantity*t.price-t.fees) ELSE 0 END),0) AS total FROM transactions t JOIN holdings h ON h._id=t.holding_id";
        if (accountId > 0) return query(sql + " WHERE t.account_id=? GROUP BY h.currency ORDER BY h.currency", new String[]{String.valueOf(accountId)});
        return query(sql + " GROUP BY h.currency ORDER BY h.currency", null);
    }
    String firstPurchaseDate(long holdingId) {
        String openedOn = "";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT opened_on FROM holdings WHERE _id=?", new String[]{String.valueOf(holdingId)})) {
            if (c.moveToFirst() && c.getString(0) != null) openedOn = c.getString(0);
        }
        String firstTrade = "";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MIN(trade_date) FROM transactions WHERE holding_id=? AND side='买入'", new String[]{String.valueOf(holdingId)})) {
            if (c.moveToFirst() && c.getString(0) != null) firstTrade = c.getString(0);
        }
        if (openedOn.isEmpty()) return firstTrade;
        if (firstTrade.isEmpty()) return openedOn;
        return openedOn.compareTo(firstTrade) <= 0 ? openedOn : firstTrade;
    }
    JSONArray goals() { return query("SELECT * FROM expense_goals ORDER BY _id", null); }
    JSONArray assetIndex(String search) {
        String q = search == null ? "" : search.trim();
        // Escape LIKE wildcards: searching "%" used to match every row in the index.
        String pattern = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return query("SELECT * FROM asset_index WHERE code LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\' ORDER BY market,code",
                new String[]{pattern, pattern});
    }

    long addAccount(String name) {
        ContentValues cv = new ContentValues(); cv.put("name", name.trim()); cv.put("created_at", Instant.now().toString());
        return getWritableDatabase().insertOrThrow("accounts", null, cv);
    }
    long saveHolding(long id, String name, String code, String market, String currency, double quantity,
                     double cost, double annualDividendPerUnit, double taxRate, long accountId,
                     String openedOn, String taxMode, String costMethod) {
        ContentValues cv = new ContentValues(); cv.put("name", name.trim()); cv.put("code", code.trim()); cv.put("market", market);
        cv.put("currency", currency); cv.put("quantity", quantity); cv.put("cost", cost);
        cv.put("annual_dividend_per_unit", annualDividendPerUnit); cv.put("tax_rate", taxRate); cv.put("account_id", accountId);
        cv.put("opened_on", openedOn == null ? "" : openedOn); cv.put("tax_mode", taxMode == null ? "auto" : taxMode);
        cv.put("cost_method", costMethod == null ? FinanceMath.COST_WEIGHTED_AVERAGE : costMethod);
        if (id == 0) {
            cv.put("created_at", Instant.now().toString());
            cv.put("initial_quantity", quantity);
            // No trades yet, so the whole position is the opening checkpoint: the typed cost is
            // the cost the replay starts from, and it must survive every later rebuild.
            cv.put("opening_cost", Double.isFinite(cost) ? cost : 0d);
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long newId = db.insertOrThrow("holdings", null, cv);
                if (!code.trim().isEmpty()) addIndexIfMissing(db, code.trim(), name.trim(), market);
                db.setTransactionSuccessful();
                return newId;
            } finally { db.endTransaction(); clearOpeningCache(); }
        }
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            double txNet = 0;
            try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(CASE WHEN side='买入' THEN quantity ELSE -quantity END),0) FROM transactions WHERE holding_id=?", new String[]{String.valueOf(id)})) {
                if (c.moveToFirst()) txNet = c.getDouble(0);
            }
            cv.put("initial_quantity", Math.max(0d, quantity - txNet));
            db.update("holdings", cv, "_id=?", new String[]{String.valueOf(id)});
            // The editor lets the cost be typed directly, which no trade would justify. Recovering
            // the opening from the stored result makes the typed value the number a rebuild
            // reproduces, instead of something the next rebuild overwrites.
            syncOpeningCost(db, id);
            ContentValues reassigned = new ContentValues(); reassigned.put("account_id", accountId);
            db.update("dividends", reassigned, "holding_id=?", new String[]{String.valueOf(id)});
            db.update("transactions", reassigned, "holding_id=?", new String[]{String.valueOf(id)});
            if (!code.trim().isEmpty()) addIndexIfMissing(db, code.trim(), name.trim(), market);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); clearOpeningCache(); }
        return id;
    }
    private void addIndexIfMissing(SQLiteDatabase db, String code, String name, String market) {
        ContentValues cv = new ContentValues(); cv.put("code", code); cv.put("name", name); cv.put("market", market);
        db.insertWithOnConflict("asset_index", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }
    boolean updateEstimatedAnnualDividend(long id, String code, String market, double perUnit) {
        if (id <= 0 || code == null || code.trim().isEmpty() || market == null ||
                !Double.isFinite(perUnit) || perUnit <= 0d) return false;
        ContentValues cv = new ContentValues(); cv.put("annual_dividend_per_unit", perUnit);
        return getWritableDatabase().update("holdings", cv, "_id=? AND code=? AND market=?",
                new String[]{String.valueOf(id), code.trim(), market}) > 0;
    }
    /**
     * Marks one dividend received and, when an amount is given, folds it into holding cost.
     *
     * <p>Both writes share a transaction. They used to be two independent statements, so a
     * failure in between could leave the dividend marked received while the cost stayed
     * unadjusted — a silent, permanent mismatch between received money and cost basis.
     */
    void markDividendReceived(long id, Double actualReceivedAmount) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long holdingId = 0;
            try (Cursor c = db.rawQuery("SELECT holding_id FROM dividends WHERE _id=?", new String[]{String.valueOf(id)})) {
                if (!c.moveToFirst()) return; holdingId = c.getLong(0);
            }
            ContentValues cv = new ContentValues(); cv.put("status", "received");
            if (actualReceivedAmount != null) {
                if (!Double.isFinite(actualReceivedAmount) || actualReceivedAmount < 0d) throw new IllegalArgumentException("实际到账金额不合法");
                cv.put("received_amount", actualReceivedAmount);
            }
            db.update("dividends", cv, "_id=?", new String[]{String.valueOf(id)});
            // Rebuilt rather than decremented by the difference. Entering an amount, correcting it
            // and deleting the dividend all change the same basis, and one shared replay keeps
            // them consistent instead of each maintaining its own arithmetic.
            rebuildHolding(db, holdingId);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
/**
     * Marks every listed dividend as received in one transaction, leaving received_amount null.
     *
     * <p>Deliberately does not write a received amount: an actual amount is what dilutes the cost
     * basis, so filling one in here would silently rewrite holding cost. A batch action that the
     * user cannot itemise should not move money. Amounts stay editable one by one.
     *
     * <p>Only rows still in 'expected' are touched, so a cancelled or already-received dividend is
     * left alone and the method stays idempotent across repeated calls and duplicate ids.
     *
     * @return how many rows actually changed.
     */
    int markReceivedBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues(); cv.put("status", "received");
        Set<Long> seen = new HashSet<>();
        int changed = 0;
        db.beginTransaction();
        try {
            for (Long id : ids) {
                if (id == null || id <= 0 || !seen.add(id)) continue;
                changed += db.update("dividends", cv, "_id=? AND status='expected'", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return changed;
    }
    void deleteHolding(long id) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try { db.delete("dividends", "holding_id=?", new String[]{String.valueOf(id)}); db.delete("transactions", "holding_id=?", new String[]{String.valueOf(id)}); db.delete("holdings", "_id=?", new String[]{String.valueOf(id)}); db.delete("app_settings", "key=?", new String[]{"dividend_forecast_" + id}); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); clearOpeningCache(); }
    }
    long addDividend(long holdingId, long accountId, double perShare, double total, String currency,
                     String recordDate, String exDate, String payDate, String status, double taxRate,
                     boolean taxKnown, Double actualReceivedAmount, String note, double recordQuantity) {
        ContentValues cv = new ContentValues(); cv.put("holding_id", holdingId); cv.put("account_id", accountId);
        cv.put("amount_per_share", perShare); cv.put("total", total); cv.put("currency", currency);
        cv.put("record_date", recordDate); cv.put("ex_date", exDate); cv.put("pay_date", payDate);
        cv.put("status", status); cv.put("note", note); cv.put("created_at", Instant.now().toString());
        cv.put("tax_rate", taxRate); cv.put("tax_known", taxKnown ? 1 : 0);
        // Frozen holding quantity at entry time; it backs the "X 股" line for a manual dividend.
        cv.put("record_quantity", recordQuantity);
        if (actualReceivedAmount != null) cv.put("received_amount", actualReceivedAmount);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long id = db.insertOrThrow("dividends", null, cv);
            rebuildHolding(db, holdingId);
            db.setTransactionSuccessful();
            return id;
        } finally { db.endTransaction(); }
    }

    boolean addPublicDividend(long holdingId, long accountId, String code, String sourceKey,
                              String dataClass, double perShare, double recordQuantity, String currency,
                              String recordDate, String exDate, String payDate, boolean payDateEstimated,
                              double taxRate, boolean taxKnown, String note) {
        SQLiteDatabase db = getWritableDatabase(); String holdingSourceKey = sourceKey + "|holding=" + holdingId;
        ContentValues cv = new ContentValues(); cv.put("holding_id", holdingId); cv.put("account_id", accountId);
        cv.put("amount_per_share", perShare); cv.put("total", perShare * recordQuantity); cv.put("currency", currency);
        cv.put("record_date", recordDate); cv.put("ex_date", exDate); cv.put("pay_date", payDate);
        cv.put("status", "expected"); cv.put("note", note); cv.put("created_at", Instant.now().toString());
        cv.put("source", "东方财富公开报表"); cv.put("source_key", holdingSourceKey); cv.put("data_class", dataClass);
        cv.put("record_quantity", recordQuantity); cv.put("pay_date_estimated", payDateEstimated ? 1 : 0);
        cv.put("tax_rate", taxRate); cv.put("tax_known", taxKnown ? 1 : 0);
        // Check-then-write must be atomic: two callers seeing "not present" would otherwise both
        // try to insert and the unique source_key index would silently reject one as -1.
        db.beginTransaction();
        try {
            long existingId = 0;
            try (Cursor c = db.rawQuery("SELECT _id, status FROM dividends WHERE source_key=?", new String[]{holdingSourceKey})) {
                if (c.moveToFirst()) { existingId = c.getLong(0); cv.put("status", c.getString(1)); }
            }
            boolean ok;
            if (existingId != 0) ok = db.update("dividends", cv, "_id=?", new String[]{String.valueOf(existingId)}) > 0;
            else ok = db.insertWithOnConflict("dividends", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1;
            db.setTransactionSuccessful();
            return ok;
        } finally { db.endTransaction(); }
    }

    double estimatedQuantityAt(long holdingId, String date) {
        Opening opening = openingAt(getReadableDatabase(), holdingId);
        if (opening == null) return 0;
        String openedOn = opening.openedOn;
        double quantity = openedOn.compareTo(date) <= 0 ? opening.quantity : 0d;
        for (TransactionRow row : transactionsUpTo(holdingId, date)) {
            if (row.tradeDate.compareTo(openedOn) < 0) continue;
            quantity = row.buy ? quantity + row.quantity : Math.max(0d, quantity - row.quantity);
        }
        return quantity;
    }

    /** One trade row in the FIFO replay; shared by estimatedQuantityAt and taxLotsAt. */
    private static final class TransactionRow { final double quantity; final String tradeDate; final boolean buy;
        TransactionRow(String side, double quantity, String tradeDate) {
            this.quantity = quantity; this.tradeDate = tradeDate; this.buy = "买入".equals(side);
        }
    }

    /**
     * Trades for a holding on or before {@code date}, oldest first. Both estimatedQuantityAt and
     * taxLotsAt need the identical ordered set, so it is read once and handed to both.
     */
    private ArrayList<TransactionRow> transactionsUpTo(long holdingId, String date) {
        ArrayList<TransactionRow> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT side,quantity,trade_date FROM transactions WHERE holding_id=? AND trade_date<=? ORDER BY trade_date,_id", new String[]{String.valueOf(holdingId), date})) {
            while (c.moveToNext()) rows.add(new TransactionRow(c.getString(0), c.getDouble(1), c.getString(2)));
        }
        return rows;
    }

    JSONArray taxLotsAt(long holdingId, String date) {
        JSONArray lots = new JSONArray();
        Opening openingRow = openingAt(getReadableDatabase(), holdingId);
        if (openingRow == null) return lots;
        String openedOn = openingRow.openedOn; double opening = openingRow.quantity;
        ArrayList<TaxLot> work = new ArrayList<>();
        if (opening > 0 && !openedOn.isEmpty() && openedOn.compareTo(date) <= 0) work.add(new TaxLot(opening, openedOn));
        for (TransactionRow row : transactionsUpTo(holdingId, date)) {
            if (row.tradeDate.compareTo(openedOn) < 0) continue;
            if (row.buy) work.add(new TaxLot(row.quantity, row.tradeDate));
            else {
                    double remaining = row.quantity;
                    for (int i = 0; i < work.size() && remaining > 1e-8; i++) {
                        TaxLot lot = work.get(i); double used = Math.min(lot.quantity, remaining);
                        lot.quantity -= used; remaining -= used;
                    }
                    for (int i = work.size() - 1; i >= 0; i--) if (work.get(i).quantity <= 1e-8) work.remove(i);
                }
        }
        for (TaxLot lot : work) if (lot.quantity > 1e-8) {
            org.json.JSONObject row = new org.json.JSONObject();
            try { row.put("quantity", lot.quantity); row.put("opened_on", lot.openedOn); lots.put(row); } catch (org.json.JSONException ignored) { }
        }
        return lots;
    }

    boolean hasCompleteTaxLotCoverage(long holdingId, String date, double holdingQuantity,
                                     boolean compareHoldingQuantity) {
        LocalDate asOf = strictIsoDate(date);
        if (asOf == null) return false;
        Opening openingRow = openingAt(getReadableDatabase(), holdingId);
        if (openingRow == null) return false;
        double openingQuantity = openingRow.quantity;
        String openedOn = openingRow.openedOn;
        if (!Double.isFinite(openingQuantity) || openingQuantity < 0d) return false;
        LocalDate openingDate = openedOn.isEmpty() ? null : strictIsoDate(openedOn);
        if ((!openedOn.isEmpty() && openingDate == null) || (openingQuantity > 1e-8 && openingDate == null)) return false;
        if (!taxTransactionsReliableAt(holdingId, asOf, openingDate)) return false;

        double expectedQuantity = estimatedQuantityAt(holdingId, asOf.toString());
        if (!Double.isFinite(expectedQuantity) || expectedQuantity < 0d) return false;
        if (compareHoldingQuantity && !FinanceMath.sameShareQuantity(expectedQuantity, holdingQuantity)) return false;

        JSONArray rows = taxLotsAt(holdingId, asOf.toString());
        ArrayList<FinanceMath.Lot> datedLots = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            org.json.JSONObject row = rows.optJSONObject(i);
            if (row == null) return false;
            LocalDate acquiredOn = strictIsoDate(row.optString("opened_on", ""));
            double quantity = row.optDouble("quantity", Double.NaN);
            if (acquiredOn == null) return false;
            datedLots.add(new FinanceMath.Lot(quantity, acquiredOn));
        }
        return FinanceMath.hasCompleteTaxLotCoverage(datedLots, expectedQuantity, asOf);
    }

    private boolean taxTransactionsReliableAt(long holdingId, LocalDate asOf, LocalDate openingDate) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT side,quantity,trade_date FROM transactions WHERE holding_id=? ORDER BY trade_date,_id", new String[]{String.valueOf(holdingId)})) {
            while (c.moveToNext()) {
                String side = c.getString(0), tradeDate = c.getString(2); double quantity = c.getDouble(1);
                LocalDate date = strictIsoDate(tradeDate);
                if (date == null || quantity <= 0d || !Double.isFinite(quantity) ||
                        !("买入".equals(side) || "卖出".equals(side))) return false;
                if (openingDate != null && !date.isAfter(asOf) && date.isBefore(openingDate)) return false;
            }
        }
        return true;
    }

    private static LocalDate strictIsoDate(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            LocalDate parsed = LocalDate.parse(value);
            return parsed.toString().equals(value) ? parsed : null;
        } catch (RuntimeException ignored) { return null; }
    }

    private static final class TaxLot { double quantity; final String openedOn; TaxLot(double q, String d) { quantity = q; openedOn = d; } }
    boolean addTransaction(long holdingId, long accountId, String side, String tradeDate,
                           double quantity, double price, double fees, String note) {
        if (quantity <= 0 || price < 0 || fees < 0 || !("买入".equals(side) || "卖出".equals(side))) return false;
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            // Overselling is still rejected against the position as it stands. The replay cannot
            // be used to judge this before the row exists, and a sale that ends up larger than
            // what is held is a data-entry mistake rather than something to clamp silently.
            try (Cursor c = db.rawQuery("SELECT quantity FROM holdings WHERE _id=?", new String[]{String.valueOf(holdingId)})) {
                if (!c.moveToFirst()) return false;
                if ("卖出".equals(side) && quantity > c.getDouble(0) + 1e-8) return false;
            }
            ContentValues cv = new ContentValues(); cv.put("holding_id", holdingId); cv.put("account_id", accountId);
            cv.put("side", side); cv.put("trade_date", tradeDate); cv.put("quantity", quantity);
            cv.put("price", price); cv.put("fees", fees); cv.put("note", note); cv.put("created_at", Instant.now().toString());
            db.insertOrThrow("transactions", null, cv);
            // Recomputed rather than applied on top: an incremental step is only right for a trade
            // appended at the end, and the editor can now insert one in the middle.
            rebuildHolding(db, holdingId);
            db.setTransactionSuccessful(); return true;
        } catch (IllegalArgumentException e) { return false; }
        finally { db.endTransaction(); }
    }

    /** The holding a trade row belongs to, or 0 when the row is gone. */
    private long holdingOf(SQLiteDatabase db, String table, long id) {
        try (Cursor c = db.rawQuery("SELECT holding_id FROM " + table + " WHERE _id=?", new String[]{String.valueOf(id)})) {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        }
    }

    /**
     * Deletes one trade and recomputes the holding from what is left.
     *
     * <p>Deleting a buy that later sales depended on can leave the remaining history unable to
     * cover them. The replay clamps those sales to what was actually held and the position ends
     * at zero rather than negative, which is the honest reading of an inconsistent ledger.
     */
    boolean deleteTransaction(long id) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            long holdingId = holdingOf(db, "transactions", id);
            if (holdingId <= 0 || db.delete("transactions", "_id=?", new String[]{String.valueOf(id)}) <= 0) return false;
            rebuildHolding(db, holdingId);
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }

    /** Rewrites one trade in place. Moving it to another holding rebuilds both. */
    boolean updateTransaction(long id, long holdingId, long accountId, String side, String tradeDate,
                              double quantity, double price, double fees, String note) {
        if (quantity <= 0 || price < 0 || fees < 0 || !("买入".equals(side) || "卖出".equals(side))) return false;
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            long previousHolding = holdingOf(db, "transactions", id);
            if (previousHolding <= 0) return false;
            ContentValues cv = new ContentValues(); cv.put("holding_id", holdingId); cv.put("account_id", accountId);
            cv.put("side", side); cv.put("trade_date", tradeDate); cv.put("quantity", quantity);
            cv.put("price", price); cv.put("fees", fees); cv.put("note", note);
            if (db.update("transactions", cv, "_id=?", new String[]{String.valueOf(id)}) <= 0) return false;
            rebuildHolding(db, holdingId);
            if (previousHolding != holdingId) rebuildHolding(db, previousHolding);
            db.setTransactionSuccessful(); return true;
        } catch (IllegalArgumentException e) { return false; }
        finally { db.endTransaction(); }
    }

    /**
     * Deletes one dividend and rebuilds the holding.
     *
     * <p>A received dividend under the dividend-adjusted basis has already been deducted from the
     * cost, so removing it has to put that basis back. The rebuild does this as a side effect of
     * replaying what remains, rather than by trying to reverse one deduction.
     */
    boolean deleteDividend(long id) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            long holdingId = holdingOf(db, "dividends", id);
            if (holdingId <= 0 || db.delete("dividends", "_id=?", new String[]{String.valueOf(id)}) <= 0) return false;
            rebuildHolding(db, holdingId);
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }

    /** Rewrites one dividend in place. Moving it to another holding rebuilds both. */
    boolean updateDividend(long id, long holdingId, long accountId, double perShare, double total, String currency,
                           String recordDate, String exDate, String payDate, String status, double taxRate,
                           boolean taxKnown, Double actualReceivedAmount, String note, double recordQuantity) {
        if (total < 0 || perShare < 0) return false;
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            long previousHolding = holdingOf(db, "dividends", id);
            if (previousHolding <= 0) return false;
            ContentValues cv = new ContentValues(); cv.put("holding_id", holdingId); cv.put("account_id", accountId);
            cv.put("amount_per_share", perShare); cv.put("total", total); cv.put("currency", currency);
            cv.put("record_date", recordDate); cv.put("ex_date", exDate); cv.put("pay_date", payDate);
            cv.put("status", status); cv.put("note", note);
            cv.put("record_quantity", recordQuantity);
            cv.put("tax_rate", taxRate); cv.put("tax_known", taxKnown ? 1 : 0);
            if (actualReceivedAmount == null) cv.putNull("received_amount");
            else cv.put("received_amount", actualReceivedAmount);
            if (db.update("dividends", cv, "_id=?", new String[]{String.valueOf(id)}) <= 0) return false;
            rebuildHolding(db, holdingId);
            if (previousHolding != holdingId) rebuildHolding(db, previousHolding);
            db.setTransactionSuccessful(); return true;
        } catch (IllegalArgumentException e) { return false; }
        finally { db.endTransaction(); }
    }

    long saveGoal(long id, String name, String period, double amount, String currency) {
        ContentValues cv = new ContentValues(); cv.put("name", name.trim()); cv.put("period", period); cv.put("amount", amount); cv.put("currency", currency);
        SQLiteDatabase db = getWritableDatabase();
        if (id == 0) { cv.put("created_at", Instant.now().toString()); return db.insertOrThrow("expense_goals", null, cv); }
        db.update("expense_goals", cv, "_id=?", new String[]{String.valueOf(id)}); return id;
    }
    void deleteGoal(long id) { getWritableDatabase().delete("expense_goals", "_id=?", new String[]{String.valueOf(id)}); }
    void saveSetting(String key, String value) { ContentValues cv = new ContentValues(); cv.put("key", key); cv.put("value", value); getWritableDatabase().insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE); }
    String setting(String key, String fallback) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM app_settings WHERE key=?", new String[]{key})) {
            if (!c.moveToFirst()) return fallback;
            String value = c.getString(0);
            // A stored NULL value would otherwise flow into callers that immediately split() it.
            return value == null ? fallback : value;
        }
    }

    String exportJson() throws JSONException {
        SQLiteDatabase db = getReadableDatabase();
        db.beginTransaction();
        try {
            JSONObject root = new JSONObject();
            root.put("app", "穗账"); root.put("schema_version", DB_VERSION); root.put("exported_at", Instant.now().toString());
            root.put("accounts", rawTable(db, "SELECT * FROM accounts ORDER BY _id"));
            root.put("holdings", rawTable(db, "SELECT * FROM holdings ORDER BY _id"));
            root.put("dividends", rawTable(db, "SELECT * FROM dividends ORDER BY _id"));
            root.put("transactions", rawTable(db, "SELECT * FROM transactions ORDER BY _id"));
            root.put("expense_goals", rawTable(db, "SELECT * FROM expense_goals ORDER BY _id"));
            root.put("asset_index", rawTable(db, "SELECT * FROM asset_index ORDER BY _id"));
            root.put("app_settings", rawTable(db, "SELECT * FROM app_settings ORDER BY key"));
            return root.toString(2);
        } finally { db.endTransaction(); }
    }

    /** Full replacement is validated before opening a transaction; invalid JSON never touches live rows. */
    void importJson(String json) throws JSONException {
        if (json == null || json.length() > 10_000_000) throw new JSONException("备份为空或超过 10 MB");
        importJson(new JSONObject(json));
    }

    /**
     * Restores from an already parsed tree. The caller reaches this path when it parsed the
     * backup off a stream, which keeps a large restore from holding the raw bytes, a decoded
     * String and the parsed tree in memory at the same time.
     */
    void importJson(JSONObject root) throws JSONException {
        if (root == null) throw new JSONException("备份为空");
        int version = root.optInt("schema_version", -1);
        if (version < 1 || version > DB_VERSION) throw new JSONException("不支持的备份版本");
        JSONArray accounts = requiredArray(root, "accounts");
        JSONArray holdings = requiredArray(root, "holdings");
        JSONArray dividends = requiredArray(root, "dividends");
        JSONArray transactions = requiredArray(root, "transactions");
        JSONArray goals = version >= 2 ? requiredArray(root, "expense_goals") : optionalArray(root, "expense_goals");
        JSONArray index = version >= 2 ? requiredArray(root, "asset_index") : optionalArray(root, "asset_index");
        JSONArray settings = version >= 2 ? requiredArray(root, "app_settings") : optionalArray(root, "app_settings");
        validateBackup(accounts, holdings, dividends, transactions, goals, index, settings);
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("transactions", null, null); db.delete("dividends", null, null); db.delete("holdings", null, null);
            db.delete("accounts", null, null); db.delete("expense_goals", null, null); db.delete("asset_index", null, null); db.delete("app_settings", null, null);
            insertRows(db, "accounts", accounts); insertRows(db, "holdings", holdings);
            insertRows(db, "dividends", dividends); insertRows(db, "transactions", transactions);
            insertRows(db, "expense_goals", goals); insertRows(db, "asset_index", index); insertRows(db, "app_settings", settings);
            if (version < 5) {
                // Workbooks written before schema 5 have no opening_cost column, so every holding
                // lands on 0 and a rebuild would start the position from nothing. Deriving it from
                // the imported quantity and cost is the same recovery the migration performs.
                backfillOpeningCost(db);
            }
            if (version < 2) { seedTaxDefaults(db); seedAssetIndex(db); }
            if (version < 3) {
                db.execSQL("UPDATE holdings SET opened_on=date(created_at,'localtime'), initial_quantity=MAX(0, quantity-COALESCE((SELECT SUM(CASE WHEN t.side='买入' THEN t.quantity ELSE -t.quantity END) FROM transactions t WHERE t.holding_id=holdings._id),0)), tax_mode='manual'");
                db.execSQL("UPDATE dividends SET tax_rate=COALESCE((SELECT tax_rate FROM holdings WHERE holdings._id=dividends.holding_id),0), tax_known=1");
            }
            if (accounts.length() == 0) {
                ContentValues initial = new ContentValues(); initial.put("name", "默认账户"); initial.put("created_at", Instant.now().toString());
                db.insert("accounts", null, initial);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); clearOpeningCache(); }
    }

    private JSONArray requiredArray(JSONObject root, String name) throws JSONException {
        Object value = root.opt(name); if (!(value instanceof JSONArray)) throw new JSONException("备份缺少数组：" + name); return (JSONArray) value;
    }
    private JSONArray optionalArray(JSONObject root, String name) throws JSONException {
        Object value = root.opt(name); if (value == null || value == JSONObject.NULL) return new JSONArray();
        if (!(value instanceof JSONArray)) throw new JSONException("备份字段格式错误：" + name); return (JSONArray) value;
    }
    private void validateBackup(JSONArray accounts, JSONArray holdings, JSONArray dividends, JSONArray transactions,
                                JSONArray goals, JSONArray index, JSONArray settings) throws JSONException {
        Set<Long> accountIds = ids(accounts, "账户"); Set<Long> holdingIds = ids(holdings, "持仓");
        ids(dividends, "分红"); ids(transactions, "交易"); ids(goals, "开支目标"); ids(index, "标的索引");
        if (accounts.length() == 0) throw new JSONException("备份中没有账户");
        for (int i = 0; i < accounts.length(); i++) if (accounts.optJSONObject(i).optString("name").trim().isEmpty()) throw new JSONException("账户名称为空");
        Map<Long, Long> holdingAccounts = new HashMap<>();
        for (int i = 0; i < holdings.length(); i++) {
            JSONObject r = holdings.optJSONObject(i); requireObject(r, "持仓");
            if (r.optString("name").trim().isEmpty() || !accountIds.contains(r.optLong("account_id")))
                throw new JSONException("第 " + (i + 1) + " 条持仓的名称或所属账户无效");
            // These columns are NOT NULL; a missing one fails later inside insertRows with a bare
            // constraint error that names neither the row nor the column.
            if (r.optString("market").trim().isEmpty() || r.optString("currency").trim().isEmpty() || r.optString("created_at").trim().isEmpty())
                throw new JSONException("第 " + (i + 1) + " 条持仓缺少市场、币种或创建时间");
            String costMethod = r.optString("cost_method", FinanceMath.COST_WEIGHTED_AVERAGE);
            boolean signedCostAllowed = FinanceMath.COST_DILUTED.equals(costMethod) || FinanceMath.COST_DIVIDEND_ADJUSTED.equals(costMethod);
            if (!(FinanceMath.COST_WEIGHTED_AVERAGE.equals(costMethod) || signedCostAllowed) || r.optDouble("quantity", -1) < 0 || (!signedCostAllowed && r.optDouble("cost", -1) < 0) || r.optDouble("tax_rate", 0.10) < 0 || r.optDouble("tax_rate", 0.10) > 1 || r.optDouble("annual_dividend_per_unit", 0) < 0) throw new JSONException("持仓数量、成本算法、派息或税率无效");
            holdingAccounts.put(r.optLong("_id"), r.optLong("account_id"));
        }
        validateLinkedRows(dividends, holdingIds, accountIds, holdingAccounts, "分红");
        validateLinkedRows(transactions, holdingIds, accountIds, holdingAccounts, "交易");
        Set<String> sourceKeys = new HashSet<>();
        for (int i = 0; i < dividends.length(); i++) {
            JSONObject r = dividends.optJSONObject(i); String status = r.optString("status");
            if (!("expected".equals(status) || "received".equals(status)) || r.optString("pay_date").trim().isEmpty() || r.optDouble("total", -1) < 0 || (!r.isNull("received_amount") && r.optDouble("received_amount", -1) < 0))
                throw new JSONException("第 " + (i + 1) + " 条分红记录字段无效");
            if (r.optString("currency").trim().isEmpty() || r.optString("created_at").trim().isEmpty())
                throw new JSONException("第 " + (i + 1) + " 条分红缺少币种或创建时间");
            // dividends has a unique index on source_key; a duplicated row copied inside the
            // backup would otherwise abort the whole restore with a SQLite constraint error.
            if (!r.isNull("source_key")) {
                String key = r.optString("source_key", "");
                if (!key.isEmpty() && !sourceKeys.add(key)) throw new JSONException("第 " + (i + 1) + " 条分红的来源编号重复：" + key);
            }
        }
        for (int i = 0; i < transactions.length(); i++) {
            JSONObject r = transactions.optJSONObject(i); String side = r.optString("side");
            if (!("买入".equals(side) || "卖出".equals(side)) || r.optDouble("quantity", 0) <= 0 || r.optDouble("price", -1) < 0 || r.optDouble("fees", -1) < 0)
                throw new JSONException("第 " + (i + 1) + " 条交易记录字段无效");
            if (r.optString("created_at").trim().isEmpty()) throw new JSONException("第 " + (i + 1) + " 条交易缺少创建时间");
        }
        for (int i = 0; i < goals.length(); i++) {
            JSONObject r = goals.optJSONObject(i); requireObject(r, "目标");
            if (r.optString("name").trim().isEmpty() || !("日".equals(r.optString("period")) || "月".equals(r.optString("period")) || "年".equals(r.optString("period"))) || r.optDouble("amount", -1) < 0) throw new JSONException("开支目标无效");
        }
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < index.length(); i++) {
            JSONObject r = index.optJSONObject(i); requireObject(r, "标的索引");
            String code = r.optString("code").trim(); if (code.isEmpty() || r.optString("market").trim().isEmpty() || !codes.add(code.toUpperCase(Locale.ROOT))) throw new JSONException("本地标的索引无效或代码重复");
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < settings.length(); i++) {
            JSONObject r = settings.optJSONObject(i); requireObject(r, "设置");
            String key = r.optString("key").trim(); if (key.isEmpty() || !keys.add(key)) throw new JSONException("设置项无效或重复");
        }
        // `holdingIds` is intentionally computed here to fail early on duplicate or malformed IDs.
        if (holdingIds.size() != holdings.length()) throw new JSONException("持仓 ID 无效");
    }
    private void validateLinkedRows(JSONArray rows, Set<Long> holdingIds, Set<Long> accountIds,
                                    Map<Long, Long> holdingAccounts, String kind) throws JSONException {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i); requireObject(r, kind);
            long holdingId = r.optLong("holding_id"), accountId = r.optLong("account_id");
            if (!holdingIds.contains(holdingId) || !accountIds.contains(accountId) || holdingAccounts.get(holdingId) != accountId) throw new JSONException(kind + "关联不存在或账户不一致");
        }
    }
    private Set<Long> ids(JSONArray rows, String kind) throws JSONException {
        Set<Long> out = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i); requireObject(r, kind); long id = r.optLong("_id", -1);
            if (id <= 0 || !out.add(id)) throw new JSONException(kind + " ID 无效或重复");
        }
        return out;
    }
    private void requireObject(JSONObject row, String kind) throws JSONException { if (row == null) throw new JSONException(kind + "记录不是对象"); }
    private static final java.util.Map<String, java.util.Set<String>> KNOWN_COLUMNS;
    static {
        KNOWN_COLUMNS = new java.util.HashMap<>();
        KNOWN_COLUMNS.put("accounts", setOf("_id", "name", "created_at"));
        KNOWN_COLUMNS.put("holdings", setOf("_id", "name", "code", "market", "currency", "quantity",
                "cost", "account_id", "created_at", "annual_dividend_per_unit", "tax_rate",
                "opened_on", "initial_quantity", "tax_mode", "cost_method", "opening_cost"));
        KNOWN_COLUMNS.put("dividends", setOf("_id", "holding_id", "account_id", "amount_per_share",
                "total", "currency", "record_date", "ex_date", "pay_date", "status", "note",
                "created_at", "source", "source_key", "data_class", "record_quantity",
                "pay_date_estimated", "tax_rate", "tax_known", "received_amount"));
        KNOWN_COLUMNS.put("transactions", setOf("_id", "holding_id", "account_id", "side",
                "trade_date", "quantity", "price", "fees", "note", "created_at"));
        KNOWN_COLUMNS.put("expense_goals", setOf("_id", "name", "period", "amount", "currency", "created_at"));
        KNOWN_COLUMNS.put("asset_index", setOf("_id", "code", "name", "market"));
        KNOWN_COLUMNS.put("app_settings", setOf("key", "value"));
    }
    private static java.util.Set<String> setOf(String... cols) {
        return new java.util.HashSet<>(java.util.Arrays.asList(cols));
    }
    private void insertRows(SQLiteDatabase db, String table, JSONArray rows) throws JSONException {
        java.util.Set<String> columns = KNOWN_COLUMNS.get(table);
        if (columns == null) throw new JSONException("备份含未知表：" + table);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i); ContentValues cv = new ContentValues();
            java.util.Iterator<String> keys = row.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!columns.contains(key)) throw new JSONException("备份含未知字段：" + table + "." + key);
                Object value = row.get(key); if (value == JSONObject.NULL) cv.putNull(key);
                else if (value instanceof String) cv.put(key, (String) value);
                else if (value instanceof Integer || value instanceof Long) cv.put(key, ((Number) value).longValue());
                else if (value instanceof Number) cv.put(key, ((Number) value).doubleValue());
                else if (value instanceof Boolean) cv.put(key, (Boolean) value ? 1 : 0);
                else throw new JSONException("备份字段类型不支持：" + key);
            }
            if ("holdings".equals(table)) {
                if (!row.has("annual_dividend_per_unit")) cv.put("annual_dividend_per_unit", 0d);
                if (!row.has("tax_rate")) cv.put("tax_rate", defaultTax(row.optString("market")));
            }
            db.insertOrThrow(table, null, cv);
        }
    }
    private double defaultTax(String market) {
        if ("A股".equals(market)) return .10; if ("港股".equals(market)) return .20; if ("美股".equals(market)) return .10; return 0;
    }

    private JSONArray query(String sql, String[] args) {
        JSONArray result = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    String name = c.getColumnName(i);
                    try {
                        int type = c.getType(i);
                        if (type == Cursor.FIELD_TYPE_NULL) row.put(name, JSONObject.NULL);
                        else if (type == Cursor.FIELD_TYPE_INTEGER) row.put(name, c.getLong(i));
                        else if (type == Cursor.FIELD_TYPE_FLOAT) row.put(name, c.getDouble(i));
                        else row.put(name, c.getString(i));
                    } catch (JSONException lost) {
                        // Used to be swallowed outright, so a column missing from a row looked
                        // like a normal row that happened to have no such field.
                        android.util.Log.w("Suizhang", "备份/查询结果缺少字段：" + name, lost);
                    }
                }
                result.put(row);
            }
        }
        return result;
    }
    private JSONArray rawTable(SQLiteDatabase db, String sql) throws JSONException {
        JSONArray result = new JSONArray();
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    int type = c.getType(i); String key = c.getColumnName(i);
                    if (type == Cursor.FIELD_TYPE_NULL) row.put(key, JSONObject.NULL);
                    else if (type == Cursor.FIELD_TYPE_INTEGER) row.put(key, c.getLong(i));
                    else if (type == Cursor.FIELD_TYPE_FLOAT) row.put(key, c.getDouble(i));
                    else row.put(key, c.getString(i));
                }
                result.put(row);
            }
        }
        return result;
    }
}
