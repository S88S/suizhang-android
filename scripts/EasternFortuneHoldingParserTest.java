package cn.suizhang.ledger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Host-side OCR-shaped regression fixtures. All names, codes and values are synthetic. */
public final class EasternFortuneHoldingParserTest {
    private static int checks;

    public static void main(String[] args) {
        detailExampleOne();
        detailExampleTwo();
        detailSyntheticFund();
        compactPositionList();
        ambiguousAndUnknownInputs();
        System.out.println("东方财富 OCR 结构化夹具：" + checks + " 项断言通过（虚构数据；不是设备 ML Kit 图像 OCR 验收）。");
    }

    private static void detailExampleOne() {
        ArrayList<EasternFortuneHoldingParser.Fragment> f = new ArrayList<>();
        add(f, "持仓明细", 440, 80);
        add(f, "示例制造甲", 35, 205); add(f, "999901", 305, 205); add(f, "股东号", 755, 205); add(f, "[已遮蔽]", 900, 205);
        add(f, "持仓盈亏", 40, 310); add(f, "1230.50", 42, 350); add(f, "12.345%", 45, 385);
        add(f, "持仓数量", 38, 510); add(f, "120", 455, 510); add(f, "现价", 665, 510); add(f, "9.876", 1020, 510);
        add(f, "个股仓位", 38, 555); add(f, "8.57%", 465, 555); add(f, "成本价", 665, 555); add(f, "8.765", 1020, 555);
        add(f, "买入均价", 665, 600); add(f, "9.000", 1020, 600);
        add(f, "交易记录", 38, 675);
        add(f, "买入", 38, 720); add(f, "数量", 45, 760); add(f, "30", 455, 760); add(f, "价格", 665, 800); add(f, "8.250", 1020, 800);
        add(f, "红利入账", 38, 860); add(f, "24.00", 1020, 900);
        EasternFortuneHoldingParser.Result r = parse(f, 1220, 2656);
        check(r.layout == EasternFortuneHoldingParser.Layout.DETAIL, "识别详情页版式");
        check(r.holdings.size() == 1, "详情页仅形成一个持仓候选");
        EasternFortuneHoldingParser.Holding h = r.holdings.get(0);
        check("示例制造甲".equals(h.name.value), "识别示例证券名称");
        check("999901".equals(h.code.value), "识别名称行完整代码，不取股东号");
        check("120".equals(h.quantity.value), "使用顶部持仓数量，不用历史数量");
        check("8.765".equals(h.cost.value), "使用成本价，不用现价或买入均价");
        check(h.cost.evidence.contains("成本价") && h.cost.evidence.contains("8.765"), "成本字段保存标签和来源值");
        check(h.notices.stream().anyMatch(s -> s.contains("买入均价") && s.contains("9.000")), "成本价与买入均价不同则要求核对");
        check(h.notices.stream().anyMatch(s -> s.contains("历史交易/已到账记录不会")), "提示历史交易/到账记录不会导入");
        check(!h.code.evidence.contains("股东号") && !h.name.evidence.contains("股东号"), "字段来源不包含股东号");
    }

    private static void detailExampleTwo() {
        ArrayList<EasternFortuneHoldingParser.Fragment> f = new ArrayList<>();
        add(f, "持仓明细", 440, 80);
        add(f, "示例公用事业", 35, 205); add(f, "999902", 305, 205); add(f, "股东号", 755, 205); add(f, "[已遮蔽]", 900, 205);
        add(f, "持仓盈亏", 40, 310); add(f, "321.06", 42, 350); add(f, "7.555%", 45, 385);
        add(f, "持仓数量", 38, 510); add(f, "80", 455, 510); add(f, "可用数量", 665, 510); add(f, "80", 1020, 510);
        add(f, "现价", 38, 555); add(f, "13.579", 455, 555); add(f, "成本价", 665, 555); add(f, "12.345", 1020, 555);
        add(f, "买入均价", 665, 600); add(f, "12.345", 1020, 600);
        add(f, "交易记录", 38, 675); add(f, "红利入账", 38, 720); add(f, "2026-07-16", 700, 720);
        add(f, "数量", 38, 760); add(f, "80", 455, 760); add(f, "金额", 665, 760); add(f, "21.00", 1020, 760);
        add(f, "价格", 38, 800); add(f, "1.250", 455, 800);
        EasternFortuneHoldingParser.Result r = parse(f, 1220, 2656);
        EasternFortuneHoldingParser.Holding h = r.holdings.get(0);
        check(r.layout == EasternFortuneHoldingParser.Layout.DETAIL, "第二组虚构明细为详情页");
        check("示例公用事业".equals(h.name.value), "按代码附近标题识别示例名称");
        check("999902".equals(h.code.value), "识别第二组虚构代码");
        check("80".equals(h.quantity.value) && "12.345".equals(h.cost.value), "读取顶部数量和成本标签");
        check(!h.cost.value.equals("21.00") && !h.quantity.value.equals("21.00"), "流水金额不作为持仓数量或成本");
        check(r.tradeHistoryDetected, "检测到历史区域但不导入");
        check(h.notices.stream().anyMatch(s -> s.contains("首次建仓日期未")), "未识别日期须手动选择");
    }

    private static void detailSyntheticFund() {
        ArrayList<EasternFortuneHoldingParser.Fragment> f = new ArrayList<>();
        add(f, "持仓明细", 440, 80);
        add(f, "示例", 30, 205); add(f, "159000", 240, 205); add(f, "示例ETF甲", 30, 238);
        add(f, "持仓盈亏", 30, 350); add(f, "-95.69", 30, 405); add(f, "-5.736%", 30, 455);
        add(f, "持股天数", 30, 580); add(f, "86", 245, 580); add(f, "现价", 390, 580); add(f, "2.135", 620, 580);
        add(f, "持仓数量", 30, 625); add(f, "1200", 245, 625); add(f, "可用数量", 390, 625); add(f, "1200", 620, 625);
        add(f, "个股仓位", 30, 670); add(f, "11.04%", 245, 670); add(f, "成本价", 390, 670); add(f, "1.875", 620, 670);
        add(f, "税费合计", 30, 715); add(f, "3.48", 245, 715); add(f, "买入均价", 390, 715); add(f, "1.900", 620, 715);
        add(f, "交易记录", 30, 800);
        add(f, "红利入账", 30, 860); add(f, "数量", 30, 915); add(f, "0", 245, 915); add(f, "金额", 390, 915); add(f, "18.00", 620, 915);
        add(f, "卖出", 30, 1000); add(f, "数量", 30, 1050); add(f, "200", 245, 1050); add(f, "价格", 390, 1100); add(f, "1.812", 620, 1100);
        EasternFortuneHoldingParser.Result r = parse(f, 1220, 2656);
        EasternFortuneHoldingParser.Holding h = r.holdings.get(0);
        check(r.layout == EasternFortuneHoldingParser.Layout.DETAIL && "159000".equals(h.code.value), "识别虚构基金代码和详情版式");
        check("ETF".equals(h.market), "以 15 开头的示例代码归为 ETF 类别");
        check("示例ETF甲".equals(h.name.value), "识别虚构基金名称");
        check("1200".equals(h.quantity.value) && h.quantity.evidence.contains("持仓数量"), "数量取顶部摘要标签");
        check("1.875".equals(h.cost.value), "成本价取标签值");
        check(!"2.135".equals(h.cost.value) && !"1.900".equals(h.cost.value), "现价和买入均价不替代成本");
        check(!"0".equals(h.quantity.value) && !"200".equals(h.quantity.value), "流水数字不替代持仓数量");
        check(r.tradeHistoryDetected && h.notices.stream().anyMatch(s -> s.contains("不会从本次持仓识别导入")), "历史区域只提示、不导入");
    }

    private static void compactPositionList() {
        ArrayList<EasternFortuneHoldingParser.Fragment> f = new ArrayList<>();
        add(f, "股票/市值", 12, 70); add(f, "当日盈亏", 230, 70); add(f, "持仓盈亏", 460, 70);
        add(f, "现价/成本", 675, 70); add(f, "持仓/可用", 870, 70);
        String[][] rows = {
                {"示例银行", "120", "8.110"},
                {"示例公用", "85", "12.340"},
                {"示例制造甲", "70", "23.450"},
                {"示例航运", "60", "34.560"},
                {"示例消费", "95", "45.670"},
                {"示例制造乙", "110", "5.430"},
                {"示例能源", "130", "6.540"}
        };
        int y = 150;
        for (String[] row : rows) {
            add(f, row[0], 12, y); add(f, "--", 230, y); add(f, "1234.56", 460, y); add(f, "7.890", 675, y); add(f, row[1], 870, y);
            add(f, "17425.00", 12, y + 34); add(f, "14.619%", 460, y + 34); add(f, row[2], 675, y + 34); add(f, row[1], 870, y + 34);
            y += 84;
        }
        add(f, "场内基金", 12, y + 20); add(f, "/市值", 120, y + 58); add(f, "现价/成本", 675, y + 58); add(f, "持仓/可用", 870, y + 58);
        add(f, "示例基金", 12, y + 105); add(f, "2.013", 675, y + 105); add(f, "330", 870, y + 105);
        EasternFortuneHoldingParser.Result r = parse(f, 1000, 1800);
        check(r.layout == EasternFortuneHoldingParser.Layout.POSITION_LIST, "识别窄列持仓列表版式");
        check(r.holdings.size() == rows.length, "仅识别股票分组，不混入基金分组");
        for (int i = 0; i < rows.length; i++) {
            EasternFortuneHoldingParser.Holding h = r.holdings.get(i);
            check(rows[i][0].equals(h.name.value), "列表第" + (i + 1) + "行名称正确");
            check(rows[i][1].equals(h.quantity.value), "列表第" + (i + 1) + "行持仓数量正确");
            check(rows[i][2].equals(h.cost.value), "列表第" + (i + 1) + "行使用下行成本");
            check(h.code.value.isEmpty(), "未显示完整代码时留空而不猜码");
            check(h.cost.evidence.contains("下行") && h.quantity.evidence.contains("上行"), "行列来源值可追溯");
        }
    }

    private static void ambiguousAndUnknownInputs() {
        ArrayList<EasternFortuneHoldingParser.Fragment> ambiguous = new ArrayList<>();
        add(ambiguous, "持仓明细", 440, 80); add(ambiguous, "示例制造甲 999901 示例公用事业 999902", 20, 205);
        add(ambiguous, "持仓数量 120", 20, 510); add(ambiguous, "成本价 8.765", 20, 555);
        EasternFortuneHoldingParser.Result a = parse(ambiguous, 1220, 2656);
        check(a.holdings.get(0).code.state == EasternFortuneHoldingParser.State.AMBIGUOUS, "多代码冲突显式标记而不任取一个");
        EasternFortuneHoldingParser.Result u = parse(Arrays.asList(new EasternFortuneHoldingParser.Fragment("没有表格字段", 10, 10, 200, 35)), 1000, 1000);
        check(u.layout == EasternFortuneHoldingParser.Layout.UNKNOWN && u.holdings.get(0).quantity.value.isEmpty(), "未知版式不按数字顺序猜字段");
    }

    private static EasternFortuneHoldingParser.Result parse(List<EasternFortuneHoldingParser.Fragment> f, int w, int h) {
        return new EasternFortuneHoldingParser().parse(f, w, h);
    }

    private static void add(List<EasternFortuneHoldingParser.Fragment> out, String text, int x, int y) {
        int width = Math.max(18, text.length() * 18);
        out.add(new EasternFortuneHoldingParser.Fragment(text, x, y, x + width, y + 26));
    }

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError("FAIL " + name);
        checks++;
        System.out.println("PASS " + name);
    }
}
