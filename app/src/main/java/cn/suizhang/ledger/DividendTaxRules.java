package cn.suizhang.ledger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 分红税率的判定规则，从 MainActivity 里搬出来。
 *
 * <p>原来这部分逻辑（有效税率 30 行、估算是否可信 16 行）长在 UI 类里，而它判的是
 * 「这笔分红的税能算吗、算出来是多少」——与界面无关。搬出来之后它第一次可以被单测覆盖，
 * 而不是靠 grep 源码确认某一行还在。
 *
 * <p>依赖倒置：规则本身只认 {@link Port}，不认 LedgerDatabase。MainActivity 把 db 包一层
 * 传进来。这样规则可以脱离 Android 与 SQLite 测试，账本侧的查询仍是唯一数据来源。
 */
final class DividendTaxRules {

    /** 规则需要账本提供的三件事。刻意保持窄，避免把 LedgerDatabase 整个漏进来。 */
    interface Port {
        /** 读取设置项，缺省返回 fallback。 */
        String setting(String key, String fallback);

        /**
         * 该持仓在指定日期的 FIFO 批次，按时间正序，元素形如
         * {quantity, opened_on}。返回 null 表示读不到。
         *
         * <p>刻意返回原始行而不是 List<Lot>：原实现遇到任何一条批次的日期解析失败就
         * 整体返回 0（不是跳过该条），这个"任一条坏则整体不可算"的语义要在规则里保留，
         * 不能推给调用方去猜。
         */
        List<RawLot> taxLotsAt(long holdingId, String date);

        /**
         * 该持仓在指定日期的有效批次是否完整覆盖持仓量。
         *
         * @param includeTodayToday 由调用方决定"今天"是否计入已知批次（传 asOf 是否为今天）
         */
        boolean hasCompleteTaxLotCoverage(long holdingId, String date, double holdingQuantity,
                                         boolean includeToday);
    }

    /** 账本侧交来的原始批次行，尚未解析日期。 */
    static final class RawLot {
        final double quantity;
        final String openedOn;
        RawLot(double quantity, String openedOn) {
            this.quantity = quantity;
            this.openedOn = openedOn;
        }
    }

    /** 港股通渠道标识。非港股通时港股分红不计税。 */
    private static final String HK_CHANNEL_LINKED = "港股通";

    private DividendTaxRules() { }

    /** 把设置里的税率钳进 [0,1]。手工改过的 Excel 设置表可能塞进 5 之类越界值。 */
    static double clampRate(double rate) {
        return Math.max(0, Math.min(1, rate));
    }

    /**
     * 税率估算是否可信。false 表示这笔分红的税算不出来，界面应显示"税务未确认"
     * 而不是拿 0 顶替——后者会把未知伪装成免税。
     */
    static boolean estimateKnown(Port port, String market, String taxMode, long holdingId,
                                 double quantity, String asOf) {
        if ("manual".equals(taxMode == null ? "manual" : taxMode)) return true;
        String normalized = asOf == null || asOf.trim().isEmpty()
                ? LocalDate.now().toString() : asOf.trim();

        if ("A股".equals(market)) {
            if (!port.setting("tax_identity", "未确认").startsWith("中国内地个人")) return false;
            LocalDate date;
            try {
                date = LocalDate.parse(normalized);
            } catch (RuntimeException invalidDate) {
                return false;
            }
            // 非规范日期串（如 2025-5-1）解析后与原串不一致，视为不可用
            if (!date.toString().equals(normalized)) return false;
            return port.hasCompleteTaxLotCoverage(holdingId, normalized, quantity,
                    date.equals(LocalDate.now()));
        }
        if ("港股".equals(market)) return HK_CHANNEL_LINKED.equals(port.setting("tax_hk_channel", "未确认"));
        if ("美股".equals(market)) return !"未确认".equals(port.setting("tax_us_status", "未确认"));
        return false;
    }

    /**
     * 有效税率。无法确定时返回 0，此时 {@link #estimateKnown} 应为 false，
     * 调用方据此显示"未确认"而不是把 0 当结论。
     */
    static double effectiveRate(Port port, String market, String taxMode, String manualRateRaw,
                                long holdingId, String asOf) {
        if ("manual".equals(taxMode == null ? "manual" : taxMode)) {
            // 与原实现一致：手工税率缺失/非法时结果保持 NaN，由调用方展示层兜底
            return clampRate(parseOrNaN(manualRateRaw));
        }
        String normalized = asOf == null || asOf.trim().isEmpty()
                ? LocalDate.now().toString() : asOf.trim();

        if ("A股".equals(market)) {
            LocalDate date;
            try {
                date = LocalDate.parse(normalized);
            } catch (RuntimeException invalidDate) {
                return 0d;
            }
            if (!date.toString().equals(normalized)
                    || !estimateKnown(port, market, taxMode, holdingId, Double.NaN, normalized)) {
                return 0d;
            }
            List<RawLot> raw = port.taxLotsAt(holdingId, normalized);
            if (raw == null) return 0d;
            List<FinanceMath.Lot> lots = new ArrayList<>();
            for (RawLot lot : raw) {
                if (lot == null) return 0d;
                LocalDate opened = LedgerFormats.parseDate(lot.openedOn);
                // 原实现：任一批次日期解析失败 -> 整体 0，而非跳过该批次
                if (opened == null) return 0d;
                lots.add(new FinanceMath.Lot(lot.quantity, opened));
            }
            // 先在调用前钳制，不是调用后：aShareRecordDateTaxRate 会直接拒绝越界税率，
            // 而手工改过的 Excel 设置表可能往 tax_a_short 里写 5。
            // 用 settingDouble 而不是 parseOrNaN：设置值非法时必须回退到默认值。
            // aShareRecordDateTaxRate 拒绝越界税率（会抛异常），NaN 更会直接穿透 clamp，
            // 若这里把非法值解析成 NaN，一次手改坏设置表就会让整个页面抛错。
            return clampRate(FinanceMath.aShareRecordDateTaxRate(lots, date,
                    clampRate(settingDouble(port, "tax_a_short", .2)),
                    clampRate(settingDouble(port, "tax_a_mid", .1)),
                    clampRate(settingDouble(port, "tax_a_long", 0))));
        }
        if ("港股".equals(market)) {
            return HK_CHANNEL_LINKED.equals(port.setting("tax_hk_channel", "未确认")) ? .20d : 0d;
        }
        if ("美股".equals(market)) {
            String status = port.setting("tax_us_status", "未确认");
            if (status.startsWith("符合协定")) return .10d;
            if (status.startsWith("不适用协定")) return .30d;
            return 0d;
        }
        return 0d;
    }

    /**
     * 读取一个 double 设置项，缺失或非法时回退到 fallback。
     *
     * <p>与 MainActivity.settingDouble 语义一致：非法值（如手改的 "abc"）回落默认，
     * 不是变成 NaN。这个区别在 A 股税率上很关键——NaN 会绕过 clamp 并让下游断言抛错。
     */
    static double settingDouble(Port port, String key, double fallback) {
        try {
            return Double.parseDouble(port.setting(key, String.valueOf(fallback)));
        } catch (Exception invalid) {
            return fallback;
        }
    }

    /** 解析失败返回 NaN，交由 clampRate 的调用方语义处理（与原先 Double.parseDouble 行为一致）。 */
    private static double parseOrNaN(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (Exception invalid) {
            return Double.NaN;
        }
    }
}
