package cn.suizhang.ledger;

import java.text.DecimalFormat;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * 账本展示层的纯格式化与解析。
 *
 * 这些方法原本散落在 MainActivity 里，靠 {@code moneyFormat} 实例字段共享同一个
 * DecimalFormat。它们的共同点是不读任何 Activity 状态、不碰数据库、不碰 View，
 * 因此可以脱离 Activity 单独测试——这是它们被抽出来的唯一理由。
 *
 * 抽出前的行为被逐条保留：格式化规则、缺失值的占位符（"—"）、币种前缀的
 * 回退规则都不变。任何一处规则调整都应先改这里的单测，而不是改调用点。
 */
final class LedgerFormats {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter SHORT_MONTH_DAY = DateTimeFormatter.ofPattern("M月d日");

    /** 金额统一两位小数、四舍五入。DecimalFormat 非线程安全，每个实例独占一份。 */
    private static DecimalFormat moneyFormat() {
        DecimalFormat format = new DecimalFormat("#,##0.00");
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    }

    private LedgerFormats() {}

    /** "M月d日"；解析不出日期时原样返回，不吞掉用户的输入。 */
    static String shortDate(String value) {
        LocalDate d = parseDate(value);
        return d == null ? value : d.format(SHORT_MONTH_DAY);
    }

    /** 非法或空值返回 null，调用方据此判断而不是靠异常。 */
    static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value == null ? "" : value.trim(), ISO);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 整数不带小数点，小数保留原样；非有限值返回空串。 */
    static String plain(double value) {
        if (!Double.isFinite(value)) return "";
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return String.valueOf((long) value);
        return String.valueOf(value);
    }

    /** 币种前缀映射：CNY=¥、HKD=HK$、USD=$，其它币种用代码加空格，空值不加前缀。 */
    static String amount(String currency, double value) {
        DecimalFormat format = moneyFormat();
        String prefix;
        switch (currency == null ? "" : currency) {
            case "CNY": prefix = "¥"; break;
            case "HKD": prefix = "HK$"; break;
            case "USD": prefix = "$"; break;
            default: prefix = currency == null || currency.isEmpty() ? "" : currency + " ";
        }
        return prefix + (Double.isFinite(value) ? format.format(value) : "—");
    }

    /** 比率按百分数展示；非有限值返回 "—"。 */
    static String percent(double rate) {
        return Double.isFinite(rate) ? moneyFormat().format(rate * 100d) + "%" : "—";
    }

    /** 多币种金额用 "  /  " 连接；空集合返回 "—"。 */
    static String formatAmounts(Map<String, Double> values) {
        if (values.isEmpty()) return "—";
        ArrayList<String> parts = new ArrayList<>();
        for (Map.Entry<String, Double> e : values.entrySet()) parts.add(amount(e.getKey(), e.getValue()));
        return String.join("  /  ", parts);
    }

    /** 多币种比率用 "  /  " 连接；空集合返回 "—"。 */
    static String formatPercentMap(Map<String, Double> values) {
        if (values.isEmpty()) return "—";
        ArrayList<String> parts = new ArrayList<>();
        for (Map.Entry<String, Double> e : values.entrySet()) parts.add(e.getKey() + " " + percent(e.getValue()));
        return String.join("  /  ", parts);
    }

    /** 成本法中文名。未知值回退到加权平均，与原有行为一致。 */
    static String costMethodLabel(String method) {
        if (FinanceMath.COST_DIVIDEND_ADJUSTED.equals(method)) return "分红摊薄";
        if (FinanceMath.COST_DILUTED.equals(method)) return "摊薄成本";
        return "加权平均";
    }

    /** 成本法中文名反查常量。未知名回退到加权平均。 */
    static String costMethodValue(String label) {
        if ("分红摊薄".equals(label)) return FinanceMath.COST_DIVIDEND_ADJUSTED;
        if ("摊薄成本".equals(label)) return FinanceMath.COST_DILUTED;
        return FinanceMath.COST_WEIGHTED_AVERAGE;
    }

    /** 分币种成本收益率，只保留能算出有限值的币种。 */
    static Map<String, Double> costYieldByCurrency(Map<String, Double> annual, Map<String, Double> costs) {
        Map<String, Double> values = new TreeMap<>();
        for (String currency : costs.keySet()) if (annual.containsKey(currency)) {
            double value = FinanceMath.costYield(annual.get(currency), costs.get(currency));
            if (Double.isFinite(value)) values.put(currency, value);
        }
        return values;
    }
}
