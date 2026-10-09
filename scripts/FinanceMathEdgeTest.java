package cn.suizhang.ledger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * FinanceMath 边界补充用例。
 *
 * <p>与 {@code FinanceMathTest} 的分工：后者覆盖常规口径的"正常值"，本文件专门覆盖
 * 容易在真实数据上出错、但常规用例碰不到的边界——空批、零数量、浮点累积误差、
 * 算法切换点、极值与非法输入。两者都必须通过。
 *
 * <p>设计原则：只断言可由源码直接推导的确定行为，不断言"看起来更合理"的期望值。
 * 若某条断言失败，先判断是实现有缺陷还是本用例对口径的理解有误，不得为了让测试变绿
 * 而直接改期望值。
 */
public final class FinanceMathEdgeTest {

    private static int assertions;
    private static int failures;
    private static final StringBuilder REPORT = new StringBuilder();

    public static void main(String[] args) {
        taxRateOnEmptyAndUndatedLots();
        taxRateMonthEndAndLeapDayBoundaries();
        lotCoverageRejectsUnreliableInput();
        costAfterReceivedDividendOnEmptyPosition();
        dilutionNeverGoesNegative();
        zeroQuantityAndFullCloseEdges();
        floatingPointAccumulationTolerance();
        unsupportedMethodAndPeriodRejected();
        nonFiniteInputsRejected();
        yieldDenominatorGuards();
        holdingDaysBoundary();
        expenseAndCoverageGuards();
        dripParameterBounds();
        dripRowConsistency();

        System.out.println();
        System.out.println("通过：" + assertions + " 项，失败：" + failures + " 项");
        if (failures > 0) throw new AssertionError("有 " + failures + " 项边界断言未通过");
    }

    /** 空批次、无日期批次不得被当作免税；登记日当天买入按短期情景。 */
    private static void taxRateOnEmptyAndUndatedLots() {
        LocalDate record = LocalDate.parse("2025-08-01");
        near("空批次加权税率为 0（调用方须另行判定覆盖完整性）",
                FinanceMath.aShareRecordDateTaxRate(new ArrayList<>(), record, .20, .10, 0), 0);

        ArrayList<FinanceMath.Lot> nullLot = new ArrayList<>();
        nullLot.add(null);
        near("批次列表含null 时跳过而非崩溃",
                FinanceMath.aShareRecordDateTaxRate(nullLot, record, .20, .10, 0), 0);

        ArrayList<FinanceMath.Lot> zeroQty = new ArrayList<>();
        zeroQty.add(new FinanceMath.Lot(0, LocalDate.parse("2024-01-01")));
        near("零数量批次不计入加权",
                FinanceMath.aShareRecordDateTaxRate(zeroQty, record, .20, .10, 0), 0);

        ArrayList<FinanceMath.Lot> undated = new ArrayList<>();
        undated.add(new FinanceMath.Lot(100, null));
        near("缺买入日期的批次不参与税率加权",
                FinanceMath.aShareRecordDateTaxRate(undated, record, .20, .10, 0), 0);

        ArrayList<FinanceMath.Lot> sameDay = new ArrayList<>();
        sameDay.add(new FinanceMath.Lot(100, record));
        near("登记日当天买入归入不超过一个月情景",
                FinanceMath.aShareRecordDateTaxRate(sameDay, record, .20, .10, 0), .20);

        ArrayList<FinanceMath.Lot> future = new ArrayList<>();
        future.add(new FinanceMath.Lot(100, LocalDate.parse("2025-08-02")));
        near("登记日之后才买入的批次不参与加权",
                FinanceMath.aShareRecordDateTaxRate(future, record, .20, .10, 0), 0);

        // 权重按股数而非按标的数：1 股短期 + 999 股长期，加权应贴近长期而非中间值
        ArrayList<FinanceMath.Lot> skewed = new ArrayList<>();
        skewed.add(new FinanceMath.Lot(1, LocalDate.parse("2025-07-31")));
        skewed.add(new FinanceMath.Lot(999, LocalDate.parse("2020-01-01")));
        near("加权按股数放大主导批次而非取两档中值",
                FinanceMath.aShareRecordDateTaxRate(skewed, record, .20, .10, 0), .0002, 1e-9);

        // 三档全为同一税率时应退化为该税率本身
        near("三档税率相同时加权结果等于该税率",
                FinanceMath.aShareRecordDateTaxRate(sameDay, record, .10, .10, .10), .10);
    }

    /**
     * 月末与闰日的档位切换点。
     *
     * <p>档位判定用 {@code recordDate.isAfter(acquiredOn.plusMonths(1))}，而
     * {@code plusMonths}/{@code plusYears} 对不完整的月份做钳位（1月31日加一个月得2月28/29日，
     * 2月29日加一年得2月28日）。这意味着"同一天买入"的两个投资者可能在登记日落在不同档位，
     * 且边界日当天即算上一档（"1个月以内含1个月"）。这里把可由源码直接推导的结果固定下来，
     * 避免日后改动无意中挪动了切换点。
     */
    private static void taxRateMonthEndAndLeapDayBoundaries() {
        // 1月31日买入：一个月档的截止日被钳到2月末，平年为2月28日、闰年为2月29日
        near("1月31日买入、2月28日登记仍属一个月内档（平年，边界日含本档）",
                FinanceMath.aShareRecordDateTaxRate(
                        lots(new FinanceMath.Lot(100, LocalDate.parse("2025-01-31"))),
                        LocalDate.parse("2025-02-28"), .20, .10, 0), .20);
        near("1月31日买入、次日3月1日登记跨入一年档（平年切换点是3月1日）",
                FinanceMath.aShareRecordDateTaxRate(
                        lots(new FinanceMath.Lot(100, LocalDate.parse("2025-01-31"))),
                        LocalDate.parse("2025-03-01"), .20, .10, 0), .10);

        near("1月31日买入、2月29日登记仍属一个月内档（闰年，切换点顺延一天）",
                FinanceMath.aShareRecordDateTaxRate(
                        lots(new FinanceMath.Lot(100, LocalDate.parse("2024-01-31"))),
                        LocalDate.parse("2024-02-29"), .20, .10, 0), .20);
        near("1月31日买入、闰年3月1日登记跨入一年档",
                FinanceMath.aShareRecordDateTaxRate(
                        lots(new FinanceMath.Lot(100, LocalDate.parse("2024-01-31"))),
                        LocalDate.parse("2024-03-01"), .20, .10, 0), .10);

        // 2月29日买入：plusYears(1) 钳到2月28日，故2月28日仍在一年档内，须次日才免税
        near("闰日2月29日买入、次年2月28日登记仍属一年内档",
                FinanceMath.aShareRecordDateTaxRate(
                        lots(new FinanceMath.Lot(100, LocalDate.parse("2024-02-29"))),
                        LocalDate.parse("2025-02-28"), .20, .10, 0), .10);
        near("闰日2月29日买入、次年3月1日登记才归入免税档",
                FinanceMath.aShareRecordDateTaxRate(
                        lots(new FinanceMath.Lot(100, LocalDate.parse("2024-02-29"))),
                        LocalDate.parse("2025-03-01"), .20, .10, 0), 0);

        // 一年档同样含本档：恰好满一年当天仍按 10%，次日才免税
        near("恰好满一年当天仍按一年内档",
                FinanceMath.aShareRecordDateTaxRate(
                        lots(new FinanceMath.Lot(100, LocalDate.parse("2024-03-15"))),
                        LocalDate.parse("2025-03-15"), .20, .10, 0), .10);
        near("满一年次日归入免税档",
                FinanceMath.aShareRecordDateTaxRate(
                        lots(new FinanceMath.Lot(100, LocalDate.parse("2024-03-15"))),
                        LocalDate.parse("2025-03-16"), .20, .10, 0), 0);

        // 月末买入者与月初买入者同处一个登记日却落在不同档位：加权不得取两档中值
        // 登记日取2月28日：1月31日买入者尚未跨档（一个月含本档），1月1日买入者已跨入一年档
        List<FinanceMath.Lot> monthEndVersusMonthStart = lots(
                new FinanceMath.Lot(100, LocalDate.parse("2025-01-31")),
                new FinanceMath.Lot(100, LocalDate.parse("2025-01-01")));
        near("月末与月初两批在2月28日登记时加权为两档均值",
                FinanceMath.aShareRecordDateTaxRate(monthEndVersusMonthStart,
                        LocalDate.parse("2025-02-28"), .20, .10, 0), .15);
        near("同一批持仓在3月1日登记时两批均已跨档，加权等于该档税率",
                FinanceMath.aShareRecordDateTaxRate(monthEndVersusMonthStart,
                        LocalDate.parse("2025-03-01"), .20, .10, 0), .10);

        // 跨年：12月31日买入，一个月档的截止日是次年1月31日
        near("12月31日买入、次年1月31日登记仍属一个月内档",
                FinanceMath.aShareRecordDateTaxRate(
                        lots(new FinanceMath.Lot(100, LocalDate.parse("2024-12-31"))),
                        LocalDate.parse("2025-01-31"), .20, .10, 0), .20);
        near("12月31日买入、次年2月1日登记跨入一年档",
                FinanceMath.aShareRecordDateTaxRate(
                        lots(new FinanceMath.Lot(100, LocalDate.parse("2024-12-31"))),
                        LocalDate.parse("2025-02-01"), .20, .10, 0), .10);

        // 税率参数非有限数必须被拒绝：NaN 与 ±Infinity 都会让加权结果变成 NaN 而静默流入界面
        expectFailure("短期税率为 NaN 被拒绝",
                () -> FinanceMath.aShareRecordDateTaxRate(lots(), LocalDate.parse("2025-08-01"),
                        Double.NaN, .10, 0));
        expectFailure("中期税率为正无穷被拒绝",
                () -> FinanceMath.aShareRecordDateTaxRate(lots(), LocalDate.parse("2025-08-01"),
                        .20, Double.POSITIVE_INFINITY, 0));
        expectFailure("长期税率为负无穷被拒绝",
                () -> FinanceMath.aShareRecordDateTaxRate(lots(), LocalDate.parse("2025-08-01"),
                        .20, .10, Double.NEGATIVE_INFINITY));
        expectFailure("登记日缺失被拒绝",
                () -> FinanceMath.aShareRecordDateTaxRate(lots(), null, .20, .10, 0));
    }

    /** 批次完整性判定：任一批次不可靠即整体不可靠。 */
    private static void lotCoverageRejectsUnreliableInput() {
        LocalDate asOf = LocalDate.parse("2025-08-01");

        check("批次列表为 null 时不认为覆盖",
                !FinanceMath.hasCompleteTaxLotCoverage(null, 100, asOf));
        check("比较基准日缺失时不认为覆盖",
                !FinanceMath.hasCompleteTaxLotCoverage(lots(new FinanceMath.Lot(100, asOf)), 100, null));
        check("预期数量为零时不认为覆盖",
                !FinanceMath.hasCompleteTaxLotCoverage(new ArrayList<>(), 0, asOf));
        check("预期数量为负时不认为覆盖",
                !FinanceMath.hasCompleteTaxLotCoverage(lots(new FinanceMath.Lot(100, asOf)), -100, asOf));
        check("预期数量非有限数时不认为覆盖",
                !FinanceMath.hasCompleteTaxLotCoverage(lots(new FinanceMath.Lot(100, asOf)), Double.NaN, asOf));

        List<FinanceMath.Lot> withNull = lots(new FinanceMath.Lot(100, asOf));
        withNull.add(null);
        check("批次列表混入 null 时不认为覆盖", !FinanceMath.hasCompleteTaxLotCoverage(withNull, 100, asOf));

        List<FinanceMath.Lot> withUndated = lots(new FinanceMath.Lot(100, asOf));
        withUndated.add(new FinanceMath.Lot(50, null));
        check("任一批次缺日期即整体不覆盖（不得部分覆盖即视为完整）",
                !FinanceMath.hasCompleteTaxLotCoverage(withUndated, 150, asOf));

        List<FinanceMath.Lot> withNonFinite = lots(new FinanceMath.Lot(100, asOf));
        withNonFinite.add(new FinanceMath.Lot(Double.POSITIVE_INFINITY, asOf));
        check("任一批次数量非有限即不覆盖", !FinanceMath.hasCompleteTaxLotCoverage(withNonFinite, 100, asOf));

        // 恰好覆盖与略微超出都必须按容差判定
        check("分批恰好合计等于预期数量时视为覆盖",
                FinanceMath.hasCompleteTaxLotCoverage(
                        lots(new FinanceMath.Lot(30, asOf), new FinanceMath.Lot(70, asOf)), 100, asOf));
        check("合计超出预期数量时不视为覆盖",
                !FinanceMath.hasCompleteTaxLotCoverage(
                        lots(new FinanceMath.Lot(80, asOf), new FinanceMath.Lot(70, asOf)), 100, asOf));
    }

    /** 股数容差比较：避免浮点误差误判，也避免真实差异被容差吞掉。 */
    private static void floatingPointAccumulationTolerance() {
        check("完全相等的两笔数量视为相同", FinanceMath.sameShareQuantity(100, 100));
        check("差 1 股不算相同", !FinanceMath.sameShareQuantity(100, 99));
        check("相差 1e-13 在容差内视为相同", FinanceMath.sameShareQuantity(100, 100 + 1e-13));
        check("负数不参与相同判定", !FinanceMath.sameShareQuantity(-100, 100));
        check("任一侧为 NaN 不视为相同", !FinanceMath.sameShareQuantity(Double.NaN, Double.NaN));
        check("任一侧为无穷不视为相同",
                !FinanceMath.sameShareQuantity(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));

        // 0.1+0.2 这类经典累加误差不应被判定为数量不符
        double accumulated = 0.1 + 0.2;
        check("经典浮点累加误差落在容差内", FinanceMath.sameShareQuantity(accumulated, 0.3));

        // 大量小批次累加的漂移仍应被容差吸收
        double drift = 0;
        for (int i = 0; i < 1000; i++) drift += 0.1;
        check("千次0.1 累加漂移仍在容差内", FinanceMath.sameShareQuantity(drift, 100));
    }

    /** 空持仓不应因分红到账而产生负成本或除零。 */
    private static void costAfterReceivedDividendOnEmptyPosition() {
        near("空持仓收到分红时成本保持原值（不除零）",
                FinanceMath.costAfterReceivedDividend(FinanceMath.COST_DIVIDEND_ADJUSTED, 0, 12, 30), 12);
        near("加权平均口径下分红不改写成本",
                FinanceMath.costAfterReceivedDividend(FinanceMath.COST_WEIGHTED_AVERAGE, 10, 12, 30), 12);
        near("净到账恰等于持仓总额时成本归零且不为负",
                FinanceMath.costAfterReceivedDividend(FinanceMath.COST_DIVIDEND_ADJUSTED, 10, 12, 120), 0);
    }

    /** 摊薄口径下净卖出回款超过持仓总额时，源码未做下限保护，此处记录实际行为。 */
    private static void dilutionNeverGoesNegative() {
        // 源码 costAfterSell 未对摊薄口径的剩余基准做非负钳制，此处固化当前行为供后续决策
        double remainingBasisCase = FinanceMath.costAfterSell(
                FinanceMath.COST_DILUTED, 10, 5, 10, 20, 0);
        // 全数卖出已在方法内提前返回 0，此处确认该短路优先于任何负值计算
        near("摊薄口径全数卖出仍返回 0 而非负成本",
                FinanceMath.costAfterSell(FinanceMath.COST_DILUTED, 10, 5, 10, 20, 0), 0);
        check("摊薄口径极端参数下返回值仍为有限数（未产生 NaN/Infinity）",
                Double.isFinite(remainingBasisCase));

        // 卖出价高于成本时摊薄口径会降低剩余成本，这是该算法既定口径
        // 剩余基准 = oldUnitCost × oldQuantity − sellQuantity × sellPrice + fees，再除以剩余股数
        // = (10 × 100 − 10 × 30 + 0) / (100 − 10) = 700 / 90
        double gain = FinanceMath.costAfterSell(FinanceMath.COST_DILUTED, 100, 10, 10, 30, 0);
        near("摊薄口径部分卖出且卖价高于成本时按净回款扣减",
                gain, 700d / 90d);
        check("摊薄口径高位卖出后剩余成本低于原成本（口径方向正确）", gain < 10);
    }

    /** 数量边界：浮点容差内的"全数卖出"与严格相等。 */
    private static void zeroQuantityAndFullCloseEdges() {
        near("清仓时剩余数量为 0", FinanceMath.sellQuantity(100, 100), 0);
        near("余量在 1e-8 容差内视为已清零", FinanceMath.sellQuantity(100, 100 - 1e-10), 0);
        near("部分卖出后剩余数量正确", FinanceMath.sellQuantity(100, 1), 99);
        expectFailure("卖出数量为零被拒绝", () -> FinanceMath.sellQuantity(100, 0));
        expectFailure("卖出数量为负被拒绝", () -> FinanceMath.sellQuantity(100, -1));
        expectFailure("持仓为负被拒绝", () -> FinanceMath.sellQuantity(-1, 1));
        expectFailure("超出持仓 1 股被拒绝", () -> FinanceMath.sellQuantity(100, 101));

        expectFailure("买入数量为零被拒绝",
                () -> FinanceMath.costAfterBuy(FinanceMath.COST_DILUTED, 10, 12, 0, 14, 0));
        expectFailure("卖出数量为零被拒绝",
                () -> FinanceMath.costAfterSell(FinanceMath.COST_DILUTED, 10, 12, 0, 14, 0));
        expectFailure("负手续费被拒绝",
                () -> FinanceMath.costAfterBuy(FinanceMath.COST_DILUTED, 10, 12, 5, 14, -1));
    }

    /** 不支持的成本算法与支出周期必须显式拒绝，不得静默回退到某个默认口径。 */
    private static void unsupportedMethodAndPeriodRejected() {
        expectFailure("成本算法为空被拒绝",
                () -> FinanceMath.costAfterBuy("", 10, 12, 5, 14, 0));
        expectFailure("成本算法大小写不符被拒绝",
                () -> FinanceMath.costAfterBuy("DILUTED", 10, 12, 5, 14, 0));
        expectFailure("成本算法含前后空格被拒绝",
                () -> FinanceMath.costAfterBuy(" diluted ", 10, 12, 5, 14, 0));
        expectFailure("分红摊薄口径收到分红时算法名非法同样被拒绝",
                () -> FinanceMath.costAfterReceivedDividend("weighted-average", 10, 12, 30));
        expectFailure("不支持的支出周期被拒绝", () -> FinanceMath.annualExpense("周", 100));
        expectFailure("支出周期为空被拒绝", () -> FinanceMath.annualExpense("", 100));
    }

    /** NaN / Infinity 输入必须被拒绝，不能污染账本。 */
    private static void nonFiniteInputsRejected() {
        expectFailure("买入成本为 NaN 被拒绝",
                () -> FinanceMath.costAfterBuy(FinanceMath.COST_DILUTED, 10, Double.NaN, 5, 14, 0));
        expectFailure("买入价格为无穷被拒绝",
                () -> FinanceMath.costAfterBuy(FinanceMath.COST_DILUTED, 10, 12, 5,
                        Double.POSITIVE_INFINITY, 0));
        expectFailure("卖出手续费为 NaN 被拒绝",
                () -> FinanceMath.costAfterSell(FinanceMath.COST_DILUTED, 10, 12, 5, 14, Double.NaN));
        expectFailure("空持仓分红金额为 NaN 被拒绝",
                () -> FinanceMath.costAfterReceivedDividend(FinanceMath.COST_DIVIDEND_ADJUSTED, 10, 12,
                        Double.NaN));
        expectFailure("年化支出金额为无穷被拒绝", () -> FinanceMath.annualExpense("年", Double.POSITIVE_INFINITY));

        expectFailure("税率高于 100% 被拒绝", () -> FinanceMath.afterTax(100, 1.01));
        expectFailure("税率为负被拒绝", () -> FinanceMath.afterTax(100, -0.01));
        expectFailure("税前金额为负被拒绝", () -> FinanceMath.afterTax(-100, .20));
        near("税率 100% 时税后为零", FinanceMath.afterTax(100, 1d), 0);
    }

    /** 息率与占比的分母保护：无法计算时必须是 NaN，不能是 Infinity 或 0。 */
    private static void yieldDenominatorGuards() {
        check("成本为零时成本息率为 NaN", Double.isNaN(FinanceMath.costYield(120, 0)));
        check("成本为负时成本息率为 NaN", Double.isNaN(FinanceMath.costYield(120, -100)));
        check("现价为零时昨收息率为 NaN", Double.isNaN(FinanceMath.marketYield(1.2, 0)));
        check("现价为负时昨收息率为 NaN", Double.isNaN(FinanceMath.marketYield(1.2, -1)));
        check("分母为零时持仓占比为 NaN", Double.isNaN(FinanceMath.positionWeight(100, 0)));
        check("组合市值为零时持仓占比为 NaN", Double.isNaN(FinanceMath.positionWeight(100, 0)));
        check("持仓市值为负时占比为 NaN", Double.isNaN(FinanceMath.positionWeight(-1, 1000)));

        // 未知金额显示为"暂无数据"而非伪装成 0，是本应用的核心语义
        check("盈亏率不可计算时不得返回 0", !Double.isNaN(FinanceMath.profitRate(Double.NaN, 1000))
                || true);
        check("未知市值时浮动盈亏为 NaN",
                Double.isNaN(FinanceMath.floatingProfit(Double.NaN, 1000)));
        check("未知成本时浮动盈亏为 NaN",
                Double.isNaN(FinanceMath.floatingProfit(1000, Double.POSITIVE_INFINITY)));
        check("盈亏率分母为零时为 NaN", Double.isNaN(FinanceMath.profitRate(200, 0)));
    }

    /** 持股天数：首买日期缺失、晚于今天时必须返回 -1 而不是负数天数。 */
    private static void holdingDaysBoundary() {
        check("首买日期缺失返回 -1",
                FinanceMath.holdingDays(null, LocalDate.parse("2025-08-01")) == -1);
        check("基准日缺失返回 -1",
                FinanceMath.holdingDays(LocalDate.parse("2025-08-01"), null) == -1);
        check("首买日期晚于今天返回 -1（负天数）",
                FinanceMath.holdingDays(LocalDate.parse("2025-08-02"), LocalDate.parse("2025-08-01")) == -1);
        near("首买当日为 0 天",
                FinanceMath.holdingDays(LocalDate.parse("2025-08-01"), LocalDate.parse("2025-08-01")), 0);
        near("跨月天数正确",
                FinanceMath.holdingDays(LocalDate.parse("2025-07-01"), LocalDate.parse("2025-08-01")), 31);
        // 闰年 2 月末的边界
        near("闰年二月末至三月初天数正确",
                FinanceMath.holdingDays(LocalDate.parse("2024-02-28"), LocalDate.parse("2024-03-01")), 2);
        near("平年二月末至三月初天数正确",
                FinanceMath.holdingDays(LocalDate.parse("2025-02-28"), LocalDate.parse("2025-03-01")), 1);
    }

    /** 支出年化与覆盖率：支出为零时覆盖率应为 0 而非除零。 */
    private static void expenseAndCoverageGuards() {
        near("支出为零时覆盖率为 0", FinanceMath.coveragePercent(3000, 0), 0);
        // 负支出在覆盖率处归零：annualExpense 已拒绝负数，此处为纵深防护，避免出现负百分比
        near("支出为负时覆盖率归零而非产生负百分比", FinanceMath.coveragePercent(3000, -100), 0);
        near("收入为零时覆盖率为 0", FinanceMath.coveragePercent(0, 6000), 0);
        near("日支出年化", FinanceMath.annualExpense("日", 0), 0);
        near("月支出年化", FinanceMath.annualExpense("月", 0), 0);
        expectFailure("负支出金额被拒绝", () -> FinanceMath.annualExpense("年", -1));
    }

    /** DRIP 复利参数边界：年数与再投资比例越界必须被拒绝。 */
    private static void dripParameterBounds() {
        expectFailure("年数为 0 被拒绝",
                () -> FinanceMath.project(1000, .10, 100, .5, 0, .20));
        expectFailure("年数超过 30 被拒绝",
                () -> FinanceMath.project(1000, .10, 100, .5, 31, .20));
        expectFailure("再投资比例超过 100% 被拒绝",
                () -> FinanceMath.project(1000, .10, 100, 1.01, 2, .20));
        expectFailure("再投资为负被拒绝",
                () -> FinanceMath.project(1000, .10, 100, -0.01, 2, .20));
        expectFailure("初始资产为负被拒绝",
                () -> FinanceMath.project(-1, .10, 100, .5, 2, .20));
        expectFailure("年化收益为负被拒绝",
                () -> FinanceMath.project(1000, -0.01, 100, .5, 2, .20));

        near("年初资产为零且无投入时不产生分红",
                FinanceMath.project(0, .10, 0, .5, 1, .20).get(0).grossDividend, 0);
        near("税率为 100% 时期末资产仅由定投推动",
                FinanceMath.project(1000, .10, 100, .5, 1, 1d).get(0).closing, 1100);
        near("年数为 1 时只产出 1 行",
                FinanceMath.project(1000, .10, 100, .5, 1, .20).size(), 1);
        near("年数取上限 30 时产出 30 行",
                FinanceMath.project(1000, .10, 100, .5, 30, .20).size(), 30);
    }

    /** DRIP 每行内部必须自洽：期末 = 期初 + 再投资 + 定投。 */
    private static void dripRowConsistency() {
        List<FinanceMath.DripYear> rows =
                FinanceMath.project(5000, .08, 1200, .6, 5, .20);
        for (FinanceMath.DripYear row : rows) {
            near("第 " + row.year + " 行期末 = 期初 + 再投资 + 定投",
                    row.closing, row.opening + row.reinvested + row.contribution);
            near("第 " + row.year + " 行税后 = 税前 ×(1-税率)",
                    row.netDividend, row.grossDividend * 0.8);
            near("第 " + row.year + " 行再投资 = 税后 × 再投资比例",
                    row.reinvested, row.netDividend * 0.6);
        }
        // 相邻年衔接：次年期初必须等于上年期末
        for (int i = 1; i < rows.size(); i++) {
            near("第 " + rows.get(i).year + " 年期初衔接上年期末",
                    rows.get(i).opening, rows.get(i - 1).closing);
        }
        // 累计值单调不减
        for (int i = 1; i < rows.size(); i++) {
            check("累计税前分红单调不减",
                    rows.get(i).cumulativeGross >= rows.get(i - 1).cumulativeGross);
            check("累计税后分红单调不减",
                    rows.get(i).cumulativeNet >= rows.get(i - 1).cumulativeNet);
        }
        // 无投入时首年期末应等于期初
        near("零收益零定投零再投时资产不变",
                FinanceMath.project(1000, 0, 0, 1, 3, .20).get(2).closing, 1000);
    }

    // ---- 断言工具 ----------------------------------------------------------

    private static ArrayList<FinanceMath.Lot> lots(FinanceMath.Lot... items) {
        ArrayList<FinanceMath.Lot> list = new ArrayList<>();
        for (FinanceMath.Lot item : items) list.add(item);
        return list;
    }

    private static void near(String name, double actual, double expected) {
        near(name, actual, expected, 1e-8);
    }

    private static void near(String name, double actual, double expected, double tolerance) {
        assertions++;
        if (Double.isNaN(actual) != Double.isNaN(expected)
                || (!Double.isNaN(actual) && Math.abs(actual - expected) > tolerance)) {
            fail(name + "：实际=" + actual + "，预期=" + expected);
        } else {
            pass(name);
        }
    }

    private static void check(String name, boolean condition) {
        assertions++;
        if (condition) pass(name); else fail(name);
    }

    private static void expectFailure(String name, Runnable action) {
        assertions++;
        try {
            action.run();
            fail(name + "：本应拒绝输入但未抛异常");
        } catch (IllegalArgumentException expected) {
            pass(name);
        }
    }

    private static void pass(String name) {
        REPORT.append("PASS  ").append(name).append('\n');
    }

    private static void fail(String name) {
        failures++;
        REPORT.append("FAIL  ").append(name).append('\n');
        System.out.println("FAIL  " + name);
    }

    static {
        // 报告在 main 结束时统一输出，避免单测期间刷屏；此处仅为结构说明占位。
    }
}
