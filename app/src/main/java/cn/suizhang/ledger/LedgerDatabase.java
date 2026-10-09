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

final class LedgerDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "suizhang-ledger.db";
    private static final int DB_VERSION = 4;

    LedgerDatabase(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        createCore(db);
        ContentValues initial = new ContentValues(); initial.put("name", "默认账户"); initial.put("created_at", Instant.now().toString());
        db.insert("accounts", null, initial);
        createExtras(db);
        seedTaxDefaults(db);
        seedAssetIndex(db);
    }

    private void createCore(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE holdings (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, code TEXT, market TEXT NOT NULL, currency TEXT NOT NULL, quantity REAL NOT NULL, cost REAL NOT NULL, account_id INTEGER NOT NULL, created_at TEXT NOT NULL, annual_dividend_per_unit REAL NOT NULL DEFAULT 0, tax_rate REAL NOT NULL DEFAULT 0.10, opened_on TEXT NOT NULL DEFAULT '', initial_quantity REAL NOT NULL DEFAULT 0, tax_mode TEXT NOT NULL DEFAULT 'manual', cost_method TEXT NOT NULL DEFAULT 'weighted_average')");
        db.execSQL("CREATE TABLE dividends (_id INTEGER PRIMARY KEY AUTOINCREMENT, holding_id INTEGER NOT NULL, account_id INTEGER NOT NULL, amount_per_share REAL NOT NULL, total REAL NOT NULL, currency TEXT NOT NULL, record_date TEXT, ex_date TEXT, pay_date TEXT NOT NULL, status TEXT NOT NULL, note TEXT, created_at TEXT NOT NULL, source TEXT NOT NULL DEFAULT 'manual', source_key TEXT, data_class TEXT NOT NULL DEFAULT 'manual', record_quantity REAL NOT NULL DEFAULT 0, pay_date_estimated INTEGER NOT NULL DEFAULT 0, tax_rate REAL NOT NULL DEFAULT 0, tax_known INTEGER NOT NULL DEFAULT 0, received_amount REAL)");
        db.execSQL("CREATE TABLE transactions (_id INTEGER PRIMARY KEY AUTOINCREMENT, holding_id INTEGER NOT NULL, account_id INTEGER NOT NULL, side TEXT NOT NULL, trade_date TEXT NOT NULL, quantity REAL NOT NULL, price REAL NOT NULL, fees REAL NOT NULL, note TEXT, created_at TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_dividends_paydate ON dividends(pay_date)");
        db.execSQL("CREATE INDEX idx_dividends_recorddate ON dividends(record_date)");
        db.execSQL("CREATE INDEX idx_dividends_exdate ON dividends(ex_date)");
        db.execSQL("CREATE UNIQUE INDEX idx_dividends_source_key ON dividends(source_key) WHERE source_key IS NOT NULL");
        db.execSQL("CREATE INDEX idx_holdings_account ON holdings(account_id)");
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

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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
            db.execSQL("UPDATE holdings SET opened_on=substr(created_at,1,10), initial_quantity=MAX(0, quantity-COALESCE((SELECT SUM(CASE WHEN t.side='买入' THEN t.quantity ELSE -t.quantity END) FROM transactions t WHERE t.holding_id=holdings._id),0))");
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
    }

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
        String sql = "SELECT d.currency,d.status,SUM(CASE WHEN d.status='received' THEN COALESCE(d.received_amount,d.total) ELSE d.total END) AS total,COUNT(*) AS count FROM dividends d WHERE substr(d.pay_date,1,4)=?";
        if (accountId > 0) return query(sql + " AND d.account_id=? GROUP BY d.currency,d.status ORDER BY d.currency,d.status", new String[]{String.valueOf(year), String.valueOf(accountId)});
        return query(sql + " GROUP BY d.currency,d.status ORDER BY d.currency,d.status", new String[]{String.valueOf(year)});
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
    JSONArray transactions(long accountId, int limit) {
        String sql = "SELECT t.*,h.name AS holding_name,h.code AS holding_code FROM transactions t JOIN holdings h ON h._id=t.holding_id";
        if (accountId > 0) return query(sql + " WHERE t.account_id=? ORDER BY t.trade_date DESC,t._id DESC LIMIT " + Math.max(1, limit), new String[]{String.valueOf(accountId)});
        return query(sql + " ORDER BY t.trade_date DESC,t._id DESC LIMIT " + Math.max(1, limit), null);
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
        return query("SELECT * FROM asset_index WHERE code LIKE ? OR name LIKE ? ORDER BY market,code", new String[]{"%" + q + "%", "%" + q + "%"});
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
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long newId = db.insertOrThrow("holdings", null, cv);
                if (!code.trim().isEmpty()) addIndexIfMissing(db, code.trim(), name.trim(), market);
                db.setTransactionSuccessful();
                return newId;
            } finally { db.endTransaction(); }
        }
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            double txNet = 0;
            try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(CASE WHEN side='买入' THEN quantity ELSE -quantity END),0) FROM transactions WHERE holding_id=?", new String[]{String.valueOf(id)})) {
                if (c.moveToFirst()) txNet = c.getDouble(0);
            }
            cv.put("initial_quantity", Math.max(0d, quantity - txNet));
            db.update("holdings", cv, "_id=?", new String[]{String.valueOf(id)});
            ContentValues reassigned = new ContentValues(); reassigned.put("account_id", accountId);
            db.update("dividends", reassigned, "holding_id=?", new String[]{String.valueOf(id)});
            db.update("transactions", reassigned, "holding_id=?", new String[]{String.valueOf(id)});
            if (!code.trim().isEmpty()) addIndexIfMissing(db, code.trim(), name.trim(), market);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
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
    void markDividendReceived(long id, Double actualReceivedAmount) {
        SQLiteDatabase db = getWritableDatabase(); long holdingId = 0; boolean hadAmount = false; double oldAmount = 0d;
        try (Cursor c = db.rawQuery("SELECT holding_id,received_amount FROM dividends WHERE _id=?", new String[]{String.valueOf(id)})) {
            if (!c.moveToFirst()) return; holdingId = c.getLong(0); hadAmount = !c.isNull(1); if (hadAmount) oldAmount = c.getDouble(1);
        }
        ContentValues cv = new ContentValues(); cv.put("status", "received");
        if (actualReceivedAmount != null) {
            if (!Double.isFinite(actualReceivedAmount) || actualReceivedAmount < 0d) throw new IllegalArgumentException("实际到账金额不合法");
            cv.put("received_amount", actualReceivedAmount);
        }
        db.update("dividends", cv, "_id=?", new String[]{String.valueOf(id)});
        if (actualReceivedAmount != null) adjustCostForDividend(db, holdingId, actualReceivedAmount - (hadAmount ? oldAmount : 0d));
    }
    void deleteHolding(long id) {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try { db.delete("dividends", "holding_id=?", new String[]{String.valueOf(id)}); db.delete("transactions", "holding_id=?", new String[]{String.valueOf(id)}); db.delete("holdings", "_id=?", new String[]{String.valueOf(id)}); db.delete("app_settings", "key=?", new String[]{"dividend_forecast_" + id}); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }
    long addDividend(long holdingId, long accountId, double perShare, double total, String currency,
                     String recordDate, String exDate, String payDate, String status, double taxRate,
                     boolean taxKnown, Double actualReceivedAmount, String note) {
        ContentValues cv = new ContentValues(); cv.put("holding_id", holdingId); cv.put("account_id", accountId);
        cv.put("amount_per_share", perShare); cv.put("total", total); cv.put("currency", currency);
        cv.put("record_date", recordDate); cv.put("ex_date", exDate); cv.put("pay_date", payDate);
        cv.put("status", status); cv.put("note", note); cv.put("created_at", Instant.now().toString());
        cv.put("tax_rate", taxRate); cv.put("tax_known", taxKnown ? 1 : 0);
        if (actualReceivedAmount != null) cv.put("received_amount", actualReceivedAmount);
        SQLiteDatabase db = getWritableDatabase(); long id = db.insertOrThrow("dividends", null, cv);
        if ("received".equals(status) && actualReceivedAmount != null) adjustCostForDividend(db, holdingId, actualReceivedAmount);
        return id;
    }

    private void adjustCostForDividend(SQLiteDatabase db, long holdingId, double actualNetAmount) {
        if (actualNetAmount == 0d) return;
        try (Cursor c = db.rawQuery("SELECT quantity,cost,cost_method FROM holdings WHERE _id=?", new String[]{String.valueOf(holdingId)})) {
            if (!c.moveToFirst() || !FinanceMath.COST_DIVIDEND_ADJUSTED.equals(c.getString(2))) return;
            double quantity = c.getDouble(0), cost = c.getDouble(1);
            if (quantity <= 0d) return;
            ContentValues update = new ContentValues();
            update.put("cost", FinanceMath.costAfterReceivedDividend(FinanceMath.COST_DIVIDEND_ADJUSTED, quantity, cost, actualNetAmount));
            db.update("holdings", update, "_id=?", new String[]{String.valueOf(holdingId)});
        }
    }

    boolean addPublicDividend(long holdingId, long accountId, String code, String sourceKey,
                              String dataClass, double perShare, double recordQuantity, String currency,
                              String recordDate, String exDate, String payDate, boolean payDateEstimated,
                              double taxRate, boolean taxKnown, String note) {
        SQLiteDatabase db = getWritableDatabase(); String holdingSourceKey = sourceKey + "|holding=" + holdingId;
        long existingId = 0; String existingStatus = "expected";
        try (Cursor c = db.rawQuery("SELECT _id FROM dividends WHERE source_key=?", new String[]{holdingSourceKey})) {
            if (c.moveToFirst()) existingId = c.getLong(0);
        }
        if (existingId != 0) {
            try (Cursor c = db.rawQuery("SELECT status FROM dividends WHERE _id=?", new String[]{String.valueOf(existingId)})) {
                if (c.moveToFirst()) existingStatus = c.getString(0);
            }
        }
        ContentValues cv = new ContentValues(); cv.put("holding_id", holdingId); cv.put("account_id", accountId);
        cv.put("amount_per_share", perShare); cv.put("total", perShare * recordQuantity); cv.put("currency", currency);
        cv.put("record_date", recordDate); cv.put("ex_date", exDate); cv.put("pay_date", payDate);
        cv.put("status", existingStatus); cv.put("note", note); cv.put("created_at", Instant.now().toString());
        cv.put("source", "东方财富公开报表"); cv.put("source_key", holdingSourceKey); cv.put("data_class", dataClass);
        cv.put("record_quantity", recordQuantity); cv.put("pay_date_estimated", payDateEstimated ? 1 : 0);
        cv.put("tax_rate", taxRate); cv.put("tax_known", taxKnown ? 1 : 0);
        if (existingId != 0) return db.update("dividends", cv, "_id=?", new String[]{String.valueOf(existingId)}) > 0;
        return db.insert("dividends", null, cv) != -1;
    }

    double estimatedQuantityAt(long holdingId, String date) {
        double quantity = 0;
        String openedOn = "";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT initial_quantity,opened_on FROM holdings WHERE _id=?", new String[]{String.valueOf(holdingId)})) {
            if (!c.moveToFirst()) return 0;
            openedOn = c.getString(1) == null ? "" : c.getString(1);
            if (openedOn.compareTo(date) <= 0) quantity = c.getDouble(0);
        }
        try (Cursor c = getReadableDatabase().rawQuery("SELECT side,quantity,trade_date FROM transactions WHERE holding_id=? AND trade_date<=? ORDER BY trade_date,_id", new String[]{String.valueOf(holdingId), date})) {
            while (c.moveToNext()) {
                if (c.getString(2).compareTo(openedOn) < 0) continue;
                double q = c.getDouble(1);
                quantity = "买入".equals(c.getString(0)) ? quantity + q : Math.max(0, quantity - q);
            }
        }
        return quantity;
    }

    JSONArray taxLotsAt(long holdingId, String date) {
        JSONArray lots = new JSONArray();
        double opening = 0; String openedOn = "";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT initial_quantity,opened_on FROM holdings WHERE _id=?", new String[]{String.valueOf(holdingId)})) {
            if (!c.moveToFirst()) return lots;
            openedOn = c.getString(1) == null ? "" : c.getString(1); opening = c.getDouble(0);
        }
        ArrayList<TaxLot> work = new ArrayList<>();
        if (opening > 0 && !openedOn.isEmpty() && openedOn.compareTo(date) <= 0) work.add(new TaxLot(opening, openedOn));
        try (Cursor c = getReadableDatabase().rawQuery("SELECT side,quantity,trade_date FROM transactions WHERE holding_id=? AND trade_date<=? ORDER BY trade_date,_id", new String[]{String.valueOf(holdingId), date})) {
            while (c.moveToNext()) {
                String side = c.getString(0), tradeDate = c.getString(2); double q = c.getDouble(1);
                if (tradeDate.compareTo(openedOn) < 0) continue;
                if ("买入".equals(side)) work.add(new TaxLot(q, tradeDate));
                else {
                    double remaining = q;
                    for (int i = 0; i < work.size() && remaining > 1e-8; i++) {
                        TaxLot lot = work.get(i); double used = Math.min(lot.quantity, remaining);
                        lot.quantity -= used; remaining -= used;
                    }
                    for (int i = work.size() - 1; i >= 0; i--) if (work.get(i).quantity <= 1e-8) work.remove(i);
                }
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
        double openingQuantity; String openedOn;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT initial_quantity,opened_on FROM holdings WHERE _id=?", new String[]{String.valueOf(holdingId)})) {
            if (!c.moveToFirst()) return false;
            openingQuantity = c.getDouble(0); openedOn = c.getString(1) == null ? "" : c.getString(1);
        }
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
            double oldQty, oldCost; String costMethod;
            try (Cursor c = db.rawQuery("SELECT quantity,cost,cost_method FROM holdings WHERE _id=?", new String[]{String.valueOf(holdingId)})) {
                if (!c.moveToFirst()) return false; oldQty = c.getDouble(0); oldCost = c.getDouble(1); costMethod = c.getString(2);
            }
            double newQty, newCost = oldCost;
            if ("买入".equals(side)) {
                newQty = oldQty + quantity;
                newCost = FinanceMath.costAfterBuy(costMethod, oldQty, oldCost, quantity, price, fees);
            } else {
                if (quantity > oldQty + 1e-8) return false;
                newQty = FinanceMath.sellQuantity(oldQty, quantity);
                newCost = FinanceMath.costAfterSell(costMethod, oldQty, oldCost, quantity, price, fees);
            }
            ContentValues cv = new ContentValues(); cv.put("holding_id", holdingId); cv.put("account_id", accountId);
            cv.put("side", side); cv.put("trade_date", tradeDate); cv.put("quantity", quantity);
            cv.put("price", price); cv.put("fees", fees); cv.put("note", note); cv.put("created_at", Instant.now().toString());
            db.insertOrThrow("transactions", null, cv);
            ContentValues update = new ContentValues(); update.put("quantity", newQty); update.put("cost", newCost);
            db.update("holdings", update, "_id=?", new String[]{String.valueOf(holdingId)});
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
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM app_settings WHERE key=?", new String[]{key})) { return c.moveToFirst() ? c.getString(0) : fallback; }
    }

    String exportJson() throws JSONException {
        SQLiteDatabase db = getReadableDatabase(); JSONObject root = new JSONObject();
        root.put("app", "穗账"); root.put("schema_version", DB_VERSION); root.put("exported_at", Instant.now().toString());
        root.put("accounts", rawTable(db, "SELECT * FROM accounts ORDER BY _id"));
        root.put("holdings", rawTable(db, "SELECT * FROM holdings ORDER BY _id"));
        root.put("dividends", rawTable(db, "SELECT * FROM dividends ORDER BY _id"));
        root.put("transactions", rawTable(db, "SELECT * FROM transactions ORDER BY _id"));
        root.put("expense_goals", rawTable(db, "SELECT * FROM expense_goals ORDER BY _id"));
        root.put("asset_index", rawTable(db, "SELECT * FROM asset_index ORDER BY _id"));
        root.put("app_settings", rawTable(db, "SELECT * FROM app_settings ORDER BY key"));
        return root.toString(2);
    }

    /** Full replacement is validated before opening a transaction; invalid JSON never touches live rows. */
    void importJson(String json) throws JSONException {
        if (json == null || json.length() > 10_000_000) throw new JSONException("备份为空或超过 10 MB");
        JSONObject root = new JSONObject(json);
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
            if (version < 2) { seedTaxDefaults(db); seedAssetIndex(db); }
            if (version < 3) {
                db.execSQL("UPDATE holdings SET opened_on=substr(created_at,1,10), initial_quantity=MAX(0, quantity-COALESCE((SELECT SUM(CASE WHEN t.side='买入' THEN t.quantity ELSE -t.quantity END) FROM transactions t WHERE t.holding_id=holdings._id),0)), tax_mode='manual'");
                db.execSQL("UPDATE dividends SET tax_rate=COALESCE((SELECT tax_rate FROM holdings WHERE holdings._id=dividends.holding_id),0), tax_known=1");
            }
            if (accounts.length() == 0) {
                ContentValues initial = new ContentValues(); initial.put("name", "默认账户"); initial.put("created_at", Instant.now().toString());
                db.insert("accounts", null, initial);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
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
            if (r.optString("name").trim().isEmpty() || !accountIds.contains(r.optLong("account_id"))) throw new JSONException("持仓名称或所属账户无效");
            String costMethod = r.optString("cost_method", FinanceMath.COST_WEIGHTED_AVERAGE);
            boolean signedCostAllowed = FinanceMath.COST_DILUTED.equals(costMethod) || FinanceMath.COST_DIVIDEND_ADJUSTED.equals(costMethod);
            if (!(FinanceMath.COST_WEIGHTED_AVERAGE.equals(costMethod) || signedCostAllowed) || r.optDouble("quantity", -1) < 0 || (!signedCostAllowed && r.optDouble("cost", -1) < 0) || r.optDouble("tax_rate", 0.10) < 0 || r.optDouble("tax_rate", 0.10) > 1 || r.optDouble("annual_dividend_per_unit", 0) < 0) throw new JSONException("持仓数量、成本算法、派息或税率无效");
            holdingAccounts.put(r.optLong("_id"), r.optLong("account_id"));
        }
        validateLinkedRows(dividends, holdingIds, accountIds, holdingAccounts, "分红");
        validateLinkedRows(transactions, holdingIds, accountIds, holdingAccounts, "交易");
        for (int i = 0; i < dividends.length(); i++) {
            JSONObject r = dividends.optJSONObject(i); String status = r.optString("status");
            if (!("expected".equals(status) || "received".equals(status)) || r.optString("pay_date").trim().isEmpty() || r.optDouble("total", -1) < 0 || (!r.isNull("received_amount") && r.optDouble("received_amount", -1) < 0)) throw new JSONException("分红记录字段无效");
        }
        for (int i = 0; i < transactions.length(); i++) {
            JSONObject r = transactions.optJSONObject(i); String side = r.optString("side");
            if (!("买入".equals(side) || "卖出".equals(side)) || r.optDouble("quantity", 0) <= 0 || r.optDouble("price", -1) < 0 || r.optDouble("fees", -1) < 0) throw new JSONException("交易记录字段无效");
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
    private void insertRows(SQLiteDatabase db, String table, JSONArray rows) throws JSONException {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i); ContentValues cv = new ContentValues();
            java.util.Iterator<String> keys = row.keys();
            while (keys.hasNext()) {
                String key = keys.next();
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
            if ("asset_index".equals(table)) db.insertOrThrow(table, null, cv);
            else db.insertOrThrow(table, null, cv);
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
                    try {
                        int type = c.getType(i); String name = c.getColumnName(i);
                        if (type == Cursor.FIELD_TYPE_NULL) row.put(name, JSONObject.NULL);
                        else if (type == Cursor.FIELD_TYPE_INTEGER) row.put(name, c.getLong(i));
                        else if (type == Cursor.FIELD_TYPE_FLOAT) row.put(name, c.getDouble(i));
                        else row.put(name, c.getString(i));
                    } catch (JSONException ignored) { }
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
