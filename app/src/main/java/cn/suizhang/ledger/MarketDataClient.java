package cn.suizhang.ledger;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional public-data enrichment. Only public ticker codes or search words leave the device. */
final class MarketDataClient {
    static final long QUOTE_TTL_MS = 15 * 60_000L;
    static final long DIVIDEND_TTL_MS = 24 * 60 * 60_000L;
    private static final int TIMEOUT_MS = 8_000;
    private final SharedPreferences cache;

    MarketDataClient(Context context) {
        cache = context.getSharedPreferences("public_market_cache", Context.MODE_PRIVATE);
    }

    static final class Security {
        final String code, name, market, currency;
        Security(String code, String name, String market, String currency) {
            this.code = code; this.name = name; this.market = market; this.currency = currency;
        }
    }
    static final class Quote {
        final String code, name, market, currency;
        final double price, changePercent;
        final long updatedAt;
        final boolean stale;
        Quote(String code, String name, String market, String currency, double price,
              double changePercent, long updatedAt, boolean stale) {
            this.code = code; this.name = name; this.market = market; this.currency = currency;
            this.price = price; this.changePercent = changePercent; this.updatedAt = updatedAt; this.stale = stale;
        }
    }
    static final class Dividend {
        final String code, name, market, currency, period, noticeDate, recordDate, exDate, payDate, progress, sourceKey;
        final double perShare;
        final boolean payDateEstimated;
        Dividend(String code, String name, String market, String currency, String period,
                 String noticeDate, String recordDate, String exDate, String payDate,
                 String progress, double perShare, boolean payDateEstimated, String sourceKey) {
            this.code = code; this.name = name; this.market = market; this.currency = currency;
            this.period = period; this.noticeDate = noticeDate; this.recordDate = recordDate;
            this.exDate = exDate; this.payDate = payDate; this.progress = progress;
            this.perShare = perShare; this.payDateEstimated = payDateEstimated; this.sourceKey = sourceKey;
        }
    }
    static final class Snapshot<T> {
        final List<T> rows;
        final long updatedAt;
        final boolean stale;
        final String warning;
        final boolean cached;
        Snapshot(List<T> rows, long updatedAt, boolean stale, String warning) {
            this(rows, updatedAt, stale, warning, false);
        }
        Snapshot(List<T> rows, long updatedAt, boolean stale, String warning, boolean cached) {
            this.rows = rows; this.updatedAt = updatedAt; this.stale = stale; this.warning = warning; this.cached = cached;
        }
    }
    static final class CacheChoice<T> {
        final T value; final boolean stale;
        CacheChoice(T value, boolean stale) { this.value = value; this.stale = stale; }
    }

    /** Pure helper: stale cache is kept when refresh fails; callers must label it as stale. */
    static <T> CacheChoice<T> chooseCached(T networkValue, T cachedValue, long cacheTime,
                                           long now, long ttl) {
        if (networkValue != null) return new CacheChoice<>(networkValue, false);
        if (cachedValue != null) return new CacheChoice<>(cachedValue, true);
        return new CacheChoice<>(null, false);
    }

    Snapshot<Security> search(String query, String defaultMarket) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return new Snapshot<>(Collections.emptyList(), 0, false, "请输入证券名称或代码");
        String key = "search:" + q.toUpperCase(Locale.ROOT);
        CachedRaw saved = cached(key);
        if (saved != null && isCacheFresh(saved.time, System.currentTimeMillis(), 60 * 60_000L)) {
            List<Security> rows = parseSearch(saved.body, defaultMarket);
            if (!rows.isEmpty()) return new Snapshot<>(rows, saved.time, false, null, true);
        }
        String url = "https://searchapi.eastmoney.com/api/suggest/get?input=" + enc(q) + "&type=14&count=10";
        try {
            String body = request(url, false);
            List<Security> rows = parseSearch(body, defaultMarket);
            if (rows.isEmpty()) return new Snapshot<>(Collections.emptyList(), System.currentTimeMillis(), false, "搜索响应结构未识别；可离线速查或手动录入");
            save(key, body);
            return new Snapshot<>(rows, System.currentTimeMillis(), false, null);
        } catch (Exception ex) {
            List<Security> fallback = saved == null ? Collections.emptyList() : parseSearch(saved.body, defaultMarket);
            return new Snapshot<>(fallback, saved == null ? 0 : saved.time, !fallback.isEmpty(), "证券搜索暂不可用；东方财富搜索接口可能变化，结果需自行核对", !fallback.isEmpty());
        }
    }

    Snapshot<Dividend> dividends(String market, String code) {
        String normalizedMarket = market == null ? "" : market.trim();
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!("A股".equals(normalizedMarket) || "港股".equals(normalizedMarket)))
            return new Snapshot<>(Collections.emptyList(), 0, false, "本轮仅接入当前验证过的 A 股/港股分红接口；未启用美股接口");
        String key = "dividend:" + normalizedMarket + ":" + normalizedCode;
        CachedRaw saved = cached(key);
        if (saved != null && isCacheFresh(saved.time, System.currentTimeMillis(), DIVIDEND_TTL_MS)) {
            List<Dividend> rows = parseDividends(saved.body, normalizedMarket, normalizedCode);
            if (!rows.isEmpty()) return new Snapshot<>(rows, saved.time, false, null, true);
        }
        try {
            String body = request(dividendUrl(normalizedMarket, normalizedCode), false);
            List<Dividend> rows = parseDividends(body, normalizedMarket, normalizedCode);
            if (rows.isEmpty()) throw new IllegalArgumentException("响应无可识别分红行");
            save(key, body);
            return new Snapshot<>(rows, System.currentTimeMillis(), false, null);
        } catch (Exception ex) {
            List<Dividend> fallback = saved == null ? Collections.emptyList() : parseDividends(saved.body, normalizedMarket, normalizedCode);
            return new Snapshot<>(fallback, saved == null ? 0 : saved.time, !fallback.isEmpty(),
                    fallback.isEmpty() ? "分红数据暂不可用，仍可手工录入" : "刷新失败，显示本机上次缓存的旧数据", !fallback.isEmpty());
        }
    }

    Snapshot<Quote> quotes(List<Security> securities) {
        ArrayList<Security> supported = new ArrayList<>();
        for (Security s : securities) if (s != null && isQuoteMarket(s.market) && !s.code.isEmpty()) supported.add(s);
        if (supported.isEmpty()) return new Snapshot<>(Collections.emptyList(), 0, false, "当前无可查询的 A 股/港股代码");
        StringBuilder symbols = new StringBuilder();
        for (Security s : supported) { String ticker = quoteTicker(s); if (ticker.isEmpty()) continue; if (symbols.length() > 0) symbols.append(','); symbols.append(ticker); }
        if (symbols.length() == 0) return new Snapshot<>(Collections.emptyList(), 0, false, "该市场暂不支持行情查询");
        String key = "quote:" + symbols;
        CachedRaw saved = cached(key);
        if (saved != null && isCacheFresh(saved.time, System.currentTimeMillis(), QUOTE_TTL_MS)) {
            List<Quote> cachedRows = parseQuotes(saved.body, supported, saved.time, false);
            if (!cachedRows.isEmpty()) return new Snapshot<>(cachedRows, saved.time, false, null, true);
        }
        try {
            String body = request("https://qt.gtimg.cn/q=" + encPath(symbols.toString()), true);
            List<Quote> rows = parseQuotes(body, supported, System.currentTimeMillis(), false);
            if (rows.isEmpty()) throw new IllegalArgumentException("行情返回无可识别证券");
            save(key, body);
            return new Snapshot<>(rows, System.currentTimeMillis(), false, null);
        } catch (Exception ex) {
            List<Quote> fallback = saved == null ? Collections.emptyList() : parseQuotes(saved.body, supported, saved.time, true);
            return new Snapshot<>(fallback, saved == null ? 0 : saved.time, !fallback.isEmpty(),
                    fallback.isEmpty() ? "行情暂不可用；成本和账本不受影响" : "行情刷新失败，显示本机缓存价", !fallback.isEmpty());
        }
    }

    Quote cachedQuote(String code, String market) {
        for (String key : cache.getAll().keySet()) {
            if (!key.startsWith("quote:")) continue;
            CachedRaw raw = cached(key);
            if (raw == null) continue;
            Security sec = new Security(code, "", market, currency(market));
            for (Quote q : parseQuotes(raw.body, Collections.singletonList(sec), raw.time, true)) if (q.code.equalsIgnoreCase(code)) return q;
        }
        return null;
    }

    static List<Security> parseSearch(String text, String defaultMarket) {
        ArrayList<Security> out = new ArrayList<>();
        Object root;
        try { root = MiniJson.parse(stripJsonp(text)); } catch (RuntimeException e) { return out; }
        Map<?, ?> rootMap = asMap(root);
        Object table = rootMap.get("QuotationCodeTable");
        Map<?, ?> tableMap = asMap(table);
        Object data = tableMap.get("Data");
        if (!(data instanceof List)) {
            // Some revisions wrap the list in a top-level data/result property.
            data = rootMap.get("data");
            if (!(data instanceof List)) data = rootMap.get("result");
        }
        if (!(data instanceof List)) return out;
        for (Object item : (List<?>) data) {
            Map<?, ?> row = asMap(item);
            String code = str(row, "Code", "SECURITY_CODE", "code");
            String name = str(row, "Name", "SECURITY_NAME_ABBR", "name");
            if (code.isEmpty() || name.isEmpty()) continue;
            String market = marketFromClassify(str(row, "Classify", "MarketType", "market"), code, defaultMarket);
            out.add(new Security(code, name, market, currency(market)));
        }
        return out;
    }

    static List<Dividend> parseDividends(String json, String market, String code) {
        ArrayList<Dividend> out = new ArrayList<>();
        Object root;
        try { root = MiniJson.parse(stripJsonp(json)); } catch (RuntimeException e) { return out; }
        Map<?, ?> result = asMap(asMap(root).get("result"));
        Object rows = result.get("data");
        if (!(rows instanceof List)) return out;
        for (Object value : (List<?>) rows) {
            Map<?, ?> r = asMap(value);
            String name = str(r, "SECURITY_NAME_ABBR", "SECURITY_NAME", "name");
            String period = str(r, "REPORT_DATE", "ASSIGN_PERIOD", "REPORT_PERIOD");
            String notice = cleanDate(str(r, "PLAN_NOTICE_DATE", "NOTICE_DATE"));
            String record = cleanDate(str(r, "EQUITY_RECORD_DATE", "RECORD_DATE"));
            String ex = cleanDate(str(r, "EX_DIVIDEND_DATE"));
            String pay = cleanDate(str(r, "DIVIDEND_DATE", "BONUS_PAY_DATE"));
            String progress = str(r, "ASSIGN_PROGRESS");
            double amount;
            String curr;
            boolean payEstimated = false;
            if ("A股".equals(market)) {
                amount = number(r.get("PRETAX_BONUS_RMB")) / 10d; // Eastmoney field is RMB per 10 shares.
                curr = "CNY";
                if (pay.isEmpty() && !ex.isEmpty()) { try { pay = java.time.LocalDate.parse(ex).plusDays(1).toString(); payEstimated = true; } catch (Exception ignored) { } }
            } else if ("港股".equals(market)) {
                amount = parsePerShareText(str(r, "PLAN_EXPLAIN"), "港币|港元|HKD|HK\\$");
                curr = "HKD";
            } else if ("美股".equals(market)) {
                amount = parsePerShareText(str(r, "PLAN_EXPLAIN"), "美元|USD|\\$");
                curr = "USD";
            } else continue;
            if (!(amount > 0) || !Double.isFinite(amount)) continue;
            String key = dividendKey(code, period, record, ex, pay);
            out.add(new Dividend(code, name, market, curr, period, notice, record, ex, pay, progress,
                    amount, payEstimated, key));
        }
        return out;
    }

    static String dividendKey(String code, String period, String record, String ex, String pay) {
        // Pay dates can be added/corrected by the vendor later; they must not create duplicate announcements.
        return DividendCalendarRules.eventIdentity(code, period, record, ex);
    }

    static List<Quote> parseQuotes(String gbkBody, List<Security> securities, long updatedAt, boolean stale) {
        ArrayList<Quote> out = new ArrayList<>();
        if (gbkBody == null) return out;
        Pattern p = Pattern.compile("v_([^=\\s]+)=\\\"([^\\\"]*)\\\";?");
        Matcher m = p.matcher(gbkBody);
        while (m.find()) {
            String[] f = m.group(2).split("~", -1);
            if (f.length < 5) continue;
            String ticker = m.group(1);
            Security sec = null;
            for (Security s : securities) if (quoteTicker(s).equalsIgnoreCase(ticker)) { sec = s; break; }
            if (sec == null) continue;
            try {
                double price = Double.parseDouble(f[3].trim());
                double change = f.length > 32 ? Double.parseDouble(f[32].trim()) : 0d; // already percentage points, do not multiply by 100.
                if (price <= 0 || !Double.isFinite(price)) continue;
                out.add(new Quote(sec.code, f[1].trim(), sec.market, currency(sec.market), price, change, updatedAt, stale));
            } catch (Exception ignored) { }
        }
        return out;
    }

    private String dividendUrl(String market, String code) {
        String base = "https://datacenter-web.eastmoney.com/api/data/v1/get?";
        String filter;
        String report;
        String columns;
        String tail;
        if ("A股".equals(market)) {
            String six = code.replaceAll("[^0-9]", "");
            if (six.length() != 6) throw new IllegalArgumentException("A 股代码应为 6 位数字");
            report = "RPT_SHAREBONUS_DET";
            columns = "SECURITY_CODE,SECURITY_NAME_ABBR,REPORT_DATE,PLAN_NOTICE_DATE,PRETAX_BONUS_RMB,EQUITY_RECORD_DATE,EX_DIVIDEND_DATE,ASSIGN_PROGRESS";
            filter = "(SECURITY_CODE=\"" + six + "\")";
            tail = "&sortColumns=NOTICE_DATE&sortTypes=-1&source=WEB&client=WEB";
        } else {
            String hk = code.toUpperCase(Locale.ROOT).replace(".HK", "").replaceAll("[^0-9]", "");
            if (hk.length() < 1 || hk.length() > 5) throw new IllegalArgumentException("港股代码格式不正确");
            report = "RPT_HKF10_INFO_DIVIDEND";
            columns = "SECURITY_CODE,SECUCODE,PLAN_EXPLAIN,ASSIGN_PERIOD,NOTICE_DATE,RECORD_DATE,EX_DIVIDEND_DATE,DIVIDEND_DATE,ASSIGN_PROGRESS";
            filter = "(SECUCODE=\"" + String.format(Locale.US, "%05d", Integer.parseInt(hk)) + ".HK\")";
            tail = "&source=HSF10&client=PC"; // HK report rejects sortColumns.
        }
        return base + "reportName=" + report + "&columns=" + enc(columns) + "&filter=" + enc(filter)
                + "&pageNumber=1&pageSize=50" + tail;
    }

    private String request(String url, boolean gbk) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET"); connection.setConnectTimeout(TIMEOUT_MS); connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "Suizhang-Android/1.3 (public data; local-only holdings)");
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n; int total = 0;
                while ((n = in.read(buffer)) != -1) { total += n; if (total > 2_000_000) throw new IllegalStateException("响应超过 2MB"); out.write(buffer, 0, n); }
                return new String(out.toByteArray(), gbk ? Charset.forName("GBK") : StandardCharsets.UTF_8);
            }
        } finally { if (connection != null) connection.disconnect(); }
    }

    private CachedRaw cached(String key) {
        String body = cache.getString(key, null); long time = cache.getLong(key + ":time", 0);
        return body == null ? null : new CachedRaw(body, time);
    }
    private void save(String key, String body) { cache.edit().putString(key, body).putLong(key + ":time", System.currentTimeMillis()).apply(); }
    private static final class CachedRaw { final String body; final long time; CachedRaw(String body, long time) { this.body = body; this.time = time; } }

    static String quoteTicker(Security s) {
        if (s == null || s.code == null) return "";
        String code = s.code.trim();
        if ("港股".equals(s.market)) {
            String digits = code.toUpperCase(Locale.ROOT).replace(".HK", "").replaceAll("[^0-9]", "");
            try { return "hk" + String.format(Locale.US, "%05d", Integer.parseInt(digits)); }
            catch (Exception ignored) { return ""; }
        }
        if ("A股".equals(s.market)) {
            String n = code.replaceAll("[^0-9]", "");
            if (n.length() != 6) return "";
            if (n.startsWith("6")) return "sh" + n;
            if (n.startsWith("0") || n.startsWith("3")) return "sz" + n;
        }
        if ("ETF".equals(s.market) || "基金".equals(s.market)) {
            String n = code.replaceAll("[^0-9]", "");
            if (n.length() != 6) return "";
            if (n.startsWith("5") || n.startsWith("6")) return "sh" + n;
            if (n.startsWith("0") || n.startsWith("1") || n.startsWith("2") || n.startsWith("3")) return "sz" + n;
        }
        return "";
    }
    static boolean isQuoteMarket(String market) {
        return "A股".equals(market) || "港股".equals(market) || "ETF".equals(market) || "基金".equals(market);
    }
    static boolean isCacheFresh(long savedAt, long now, long ttl) {
        return savedAt > 0L && now >= savedAt && now - savedAt < ttl;
    }
    static String normalizeSearchCode(String code) {
        String value = code == null ? "" : code.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        value = value.replaceFirst("\\.HK$", "");
        if (value.matches("[0-9]{1,5}")) {
            try { return String.format(Locale.US, "%05d", Integer.parseInt(value)); }
            catch (NumberFormatException ignored) { return value; }
        }
        return value;
    }
    static Security findExactSecurity(List<Security> rows, String code) {
        String wanted = normalizeSearchCode(code);
        if (wanted.isEmpty() || rows == null) return null;
        for (Security row : rows) if (row != null && normalizeSearchCode(row.code).equals(wanted)) return row;
        return null;
    }
    static String marketFor(Security security, String fallback) {
        if (security == null || security.market == null || security.market.isEmpty())
            return fallback == null || fallback.isEmpty() ? "A股" : fallback;
        if ("基金".equals(security.market) && security.name != null && security.name.toUpperCase(Locale.ROOT).contains("ETF")) return "ETF";
        if (("ETF".equals(security.market) || "基金".equals(security.market))
                && ("ETF".equals(fallback) || "基金".equals(fallback))) return fallback;
        return security.market;
    }
    static String marketFromClassify(String classify, String code, String fallback) {
        String c = classify == null ? "" : classify.toLowerCase(Locale.ROOT);
        if (c.contains("hk") || c.contains("港")) return "港股";
        if (c.contains("us") || c.contains("美")) return "美股";
        if (c.contains("fund") || c.contains("etf")) return c.contains("etf") ? "ETF" : "基金";
        if (c.contains("a") || c.contains("stock") || c.contains("沪") || c.contains("深")) return "A股";
        String x = code == null ? "" : code.trim();
        if (x.matches("(?i).*\\.HK$") || x.matches("\\d{1,5}")) return "港股";
        if (x.matches("\\d{6}")) return "A股";
        if (x.matches("[A-Za-z]{1,8}")) return "美股";
        return fallback == null || fallback.isEmpty() ? "A股" : fallback;
    }
    static String currency(String market) {
        if ("港股".equals(market)) return "HKD";
        if ("美股".equals(market)) return "USD";
        return "CNY";
    }
    private static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; } }
    private static String encPath(String s) { return s.replace(" ", "%20"); }
    private static String stripJsonp(String s) {
        if (s == null) return "";
        String v = s.trim(); if (v.startsWith("{")) return v;
        int left = v.indexOf('('), right = v.lastIndexOf(')');
        return left >= 0 && right > left ? v.substring(left + 1, right) : v;
    }
    private static String cleanDate(String s) { if (s == null || s.isEmpty() || "null".equalsIgnoreCase(s)) return ""; return s.length() >= 10 ? s.substring(0, 10) : ""; }
    private static String norm(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT); }
    private static Map<?, ?> asMap(Object o) { return o instanceof Map ? (Map<?, ?>) o : Collections.emptyMap(); }
    private static String str(Map<?, ?> m, String... keys) {
        for (String k : keys) { Object v = m.get(k); if (v != null && !(v instanceof MiniJson.Null)) { String s = String.valueOf(v).trim(); if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s; } }
        return "";
    }
    private static double number(Object x) { try { return Double.parseDouble(String.valueOf(x).replace(",", "").trim()); } catch (Exception e) { return 0d; } }
    private static double parsePerShareText(String text, String currencyPattern) {
        if (text == null || text.isEmpty()) return 0d;
        Pattern currency = Pattern.compile("(?:" + currencyPattern + ")\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
        Matcher m = currency.matcher(text.replace(",", ""));
        if (m.find()) return number(m.group(1));
        // Only accept explicit per-share phrasing when a currency abbreviation is omitted.
        Pattern perShare = Pattern.compile("每\\s*1?\\s*股[^0-9]{0,20}([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
        m = perShare.matcher(text.replace(",", ""));
        return m.find() ? number(m.group(1)) : 0d;
    }

    /** Small strict JSON reader shared with the JVM parser tests (no platform JSON dependency). */
    static final class MiniJson {
        static final class Null { }
        private final String s; private int p;
        private MiniJson(String s) { this.s = s == null ? "" : s; }
        static Object parse(String s) { MiniJson j = new MiniJson(s); Object v = j.value(); j.ws(); if (j.p != j.s.length()) throw new IllegalArgumentException("trailing JSON"); return v; }
        private Object value() {
            ws(); if (p >= s.length()) throw new IllegalArgumentException("empty JSON"); char c = s.charAt(p);
            if (c == '{') return object(); if (c == '[') return array(); if (c == '"') return string();
            if (c == 'n') { word("null"); return new Null(); } if (c == 't') { word("true"); return Boolean.TRUE; }
            if (c == 'f') { word("false"); return Boolean.FALSE; }
            return number();
        }
        private Map<String, Object> object() {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>(); p++; ws(); if (take('}')) return m;
            do { ws(); String k = string(); ws(); need(':'); m.put(k, value()); ws(); if (take('}')) return m; need(','); } while (true);
        }
        private List<Object> array() {
            ArrayList<Object> a = new ArrayList<>(); p++; ws(); if (take(']')) return a;
            do { a.add(value()); ws(); if (take(']')) return a; need(','); } while (true);
        }
        private String string() {
            need('"'); StringBuilder b = new StringBuilder();
            while (p < s.length()) { char c = s.charAt(p++); if (c == '"') return b.toString();
                if (c == '\\') { if (p >= s.length()) throw new IllegalArgumentException("escape"); char e = s.charAt(p++);
                    switch (e) { case '"': case '\\': case '/': b.append(e); break; case 'b': b.append('\b'); break; case 'f': b.append('\f'); break; case 'n': b.append('\n'); break; case 'r': b.append('\r'); break; case 't': b.append('\t'); break; case 'u': if (p + 4 > s.length()) throw new IllegalArgumentException("unicode"); b.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; break; default: throw new IllegalArgumentException("escape"); }
                } else b.append(c);
            } throw new IllegalArgumentException("unterminated string");
        }
        private Number number() {
            int start = p; if (take('-')) { } while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
            if (take('.')) while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
            if (p < s.length() && (s.charAt(p) == 'e' || s.charAt(p) == 'E')) { p++; if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) p++; while (p < s.length() && Character.isDigit(s.charAt(p))) p++; }
            if (p == start) throw new IllegalArgumentException("number"); return Double.parseDouble(s.substring(start, p));
        }
        private void word(String w) { if (!s.startsWith(w, p)) throw new IllegalArgumentException("token"); p += w.length(); }
        private void ws() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
        private boolean take(char c) { if (p < s.length() && s.charAt(p) == c) { p++; return true; } return false; }
        private void need(char c) { ws(); if (!take(c)) throw new IllegalArgumentException("expected " + c); }
    }
}
