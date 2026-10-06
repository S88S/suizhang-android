package cn.suizhang.ledger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative, local-only parser for Eastmoney-style holding screenshots.
 * Input fragments are ML Kit text elements with image-space bounds. This class
 * intentionally has no Android, network, database, or logging dependency.
 */
final class EasternFortuneHoldingParser {
    enum Layout { DETAIL, POSITION_LIST, UNKNOWN }
    enum State { LABELED_REVIEW, COLUMN_REVIEW, MISSING, AMBIGUOUS }

    static final class Fragment {
        final String text;
        final int left, top, right, bottom;
        Fragment(String text, int left, int top, int right, int bottom) {
            this.text = text == null ? "" : text.trim();
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        int centerX() { return (left + right) / 2; }
        int centerY() { return (top + bottom) / 2; }
        int height() { return Math.max(1, bottom - top); }
    }

    static final class Field {
        final String value, label, evidence;
        final State state;
        Field(String value, String label, String evidence, State state) {
            this.value = value == null ? "" : value;
            this.label = label == null ? "" : label;
            this.evidence = evidence == null ? "" : evidence;
            this.state = state;
        }
        static Field missing(String label, String evidence) { return new Field("", label, evidence, State.MISSING); }
    }

    static final class Holding {
        Field name = Field.missing("证券名称", "未识别");
        Field code = Field.missing("证券代码", "页面未显示完整代码");
        Field quantity = Field.missing("持仓数量", "未找到明确持仓数量");
        Field cost = Field.missing("成本价", "未找到明确成本价");
        String market = "A股";
        final List<String> notices = new ArrayList<>();
        String displayName() { return name.value.isEmpty() ? "未识别名称" : name.value; }
    }

    static final class Result {
        final Layout layout;
        final List<Holding> holdings;
        final boolean tradeHistoryDetected;
        final String notice;
        Result(Layout layout, List<Holding> holdings, boolean tradeHistoryDetected, String notice) {
            this.layout = layout; this.holdings = holdings;
            this.tradeHistoryDetected = tradeHistoryDetected;
            this.notice = notice == null ? "" : notice;
        }
    }

    private static final Pattern NUMBER = Pattern.compile("(?<![\\d.])([0-9][0-9,]*(?:\\.[0-9]+)?)(%?)(?![\\d.])");
    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("(?<![0-9])([0-9]{6})(?![0-9])");
    private static final Pattern HAN_NAME = Pattern.compile("[\\u4e00-\\u9fff]{2,10}");
    private static final String[] QUANTITY_LABELS = {"持仓数量", "持有数量", "持有股数", "股票余额", "证券数量"};
    private static final String[] COST_LABELS = {"持仓成本价", "成本价"};
    private static final Set<String> NON_NAMES = new HashSet<>();
    static {
        Collections.addAll(NON_NAMES, "持仓明细", "持仓盈亏", "当日参考盈亏", "当日盈亏", "持仓数量", "持有数量", "持有股数", "股票余额", "证券数量", "可用数量", "可用", "持股天数", "个股仓位", "税费合计", "成本价", "买入均价", "交易记录", "历史清仓记录", "股票市值", "现价成本", "现价", "成本", "持仓可用", "总资产", "证券市值", "理财资产", "理财昨日收益", "委托成交", "场内基金市值", "批量加仓", "批量卖出", "股东号", "买入", "卖出", "红利入账", "股息红利差异扣税");
    }

    Result parse(List<Fragment> fragments, int imageWidth, int imageHeight) {
        List<Row> rows = groupRows(fragments == null ? Collections.emptyList() : fragments);
        StringBuilder all = new StringBuilder();
        for (Row row : rows) all.append(row.flat).append('\n');
        String normalized = all.toString().replaceAll("\\s+", "");
        int tradeRow = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).flat.replaceAll("\\s+", "").contains("交易记录")) { tradeRow = i; break; }
        }
        boolean historyDetected = tradeRow >= 0 || normalized.contains("历史清仓记录") || normalized.contains("红利入账");
        boolean detailTitle = normalized.contains("持仓明细");
        boolean listColumns = normalized.contains("持仓/可用") || normalized.contains("持仓盈亏") || normalized.contains("当日盈亏");
        boolean listHeader = normalized.contains("现价/成本") && listColumns
                && (normalized.contains("股票/市值") || normalized.contains("场内基金/市值"));
        if (detailTitle) return parseDetail(rows, tradeRow, imageWidth, imageHeight, historyDetected);
        if (listHeader) return parseList(rows, imageWidth, imageHeight, historyDetected);
        return new Result(Layout.UNKNOWN, Collections.singletonList(new Holding()), historyDetected,
                "未能可靠判断为东方财富持仓明细或持仓列表；未自动填入字段，可继续手动修正。");
    }

    private Result parseDetail(List<Row> rows, int tradeRow, int imageWidth, int imageHeight, boolean historyDetected) {
        int end = tradeRow >= 0 ? tradeRow : rows.size();
        List<Row> topRows = new ArrayList<>();
        for (int i = 0; i < end; i++) {
            Row row = rows.get(i);
            // When the trade-history divider is visible, it is the safe boundary; without it,
            // stop before the lower history area instead of searching an unbounded full screen.
            if (tradeRow >= 0 || imageHeight <= 0 || row.centerY <= imageHeight * .46) topRows.add(row);
        }
        ArrayList<String> codes = new ArrayList<>();
        ArrayList<String> codeEvidence = new ArrayList<>();
        for (Row row : topRows) {
            for (Fragment fragment : row.fragments) {
                // The stock code is adjacent to the name on the left; shareholder/account
                // identifiers live in a separate right-side column and are never candidates.
                if (imageWidth > 0 && fragment.centerX() > imageWidth * .46) continue;
                Matcher m = SIX_DIGIT_CODE.matcher(fragment.text);
                while (m.find()) {
                    String code = m.group(1);
                    if (!codes.contains(code)) { codes.add(code); codeEvidence.add("证券名称行中的完整六位代码 " + code); }
                }
            }
        }
        Holding holding = new Holding();
        if (codes.size() == 1) {
            holding.code = new Field(codes.get(0), "证券代码", codeEvidence.get(0), State.LABELED_REVIEW);
            String name = findHeaderName(topRows, codes.get(0), imageWidth);
            holding.market = marketFromCode(codes.get(0), name);
            if (!name.isEmpty()) holding.name = new Field(name, "证券名称", "持仓明细页顶部标的名称 " + name, State.LABELED_REVIEW);
        } else if (codes.size() > 1) {
            holding.code = new Field("", "证券代码", "顶部检测到多个不同六位代码，未选择其中任何一个", State.AMBIGUOUS);
            holding.name = new Field("", "证券名称", "顶部标的与代码发生冲突，请手动确认", State.AMBIGUOUS);
            holding.notices.add("检测到多个证券代码，未自动选择标的。");
        } else {
            String name = findHeaderName(topRows, "", imageWidth);
            if (!name.isEmpty()) holding.name = new Field(name, "证券名称", "持仓明细页顶部名称位置 " + name, State.LABELED_REVIEW);
            holding.notices.add("未找到可确认的完整六位证券代码；代码留空，不通过股东号或其他数字猜测。");
        }

        List<Field> quantities = labeledValues(topRows, QUANTITY_LABELS, "持仓数量");
        holding.quantity = resolveLabeled(quantities, "持仓数量");
        List<Field> costs = labeledValues(topRows, COST_LABELS, "成本价");
        holding.cost = resolveLabeled(costs, "成本价");
        List<Field> buyAverages = labeledValues(topRows, new String[]{"买入均价"}, "买入均价");
        if (holding.cost.value.isEmpty() && !buyAverages.isEmpty()) {
            holding.cost = Field.missing("成本价", "仅发现买入均价（" + buyAverages.get(0).value + "）；未将买入均价替作持仓成本价");
            holding.notices.add("页面只发现买入均价，没有可确认的成本价；成本留空，请手工决定，不会混用现价或买入均价。");
        } else if (!holding.cost.value.isEmpty()) {
            for (Field avg : buyAverages) if (!avg.value.equals(holding.cost.value)) {
                holding.notices.add("成本价与买入均价不同（成本价 " + holding.cost.value + "；买入均价 " + avg.value + "）；按“成本价”标签提供可编辑预填值，请确认。");
                break;
            }
        }
        if (holding.quantity.value.isEmpty()) holding.notices.add("未找到明确的持仓数量标签；可用数量、盈亏数字及交易记录均不会替代持仓数量。");
        if (holding.cost.value.isEmpty() && buyAverages.isEmpty()) holding.notices.add("未找到明确的成本价标签；现价、盈亏与交易记录价格不会替代成本价。");
        if (historyDetected) holding.notices.add("已检测到交易记录/已到账区域；历史交易/已到账记录不会从本次持仓识别导入。");
        holding.notices.add("首次建仓日期未从持仓页识别；保存前需要手工选择有效日期。");
        String notice = "东方财富“持仓明细”版式：只读取顶部证券名称/代码及“持仓数量”“成本价”标签；“买入均价”单独显示。股东号和交易/红利历史不展示、不导入。";
        return new Result(Layout.DETAIL, Collections.singletonList(holding), historyDetected, notice);
    }

    private Result parseList(List<Row> rows, int imageWidth, int imageHeight, boolean historyDetected) {
        int sectionStart = -1, sectionEnd = rows.size();
        for (int i = 0; i < rows.size(); i++) {
            String text = rows.get(i).flat.replaceAll("\\s+", "");
            if (sectionStart < 0 && text.contains("股票/市值")) sectionStart = i + 1;
            if (sectionStart >= 0 && i >= sectionStart && text.contains("场内基金")) { sectionEnd = i; break; }
        }
        if (sectionStart < 0) {
            for (int i = 0; i < rows.size(); i++) {
                String text = rows.get(i).flat.replaceAll("\\s+", "");
                if (text.contains("持仓") && text.contains("现价/成本")) { sectionStart = i + 1; break; }
            }
        }
        if (sectionStart < 0) sectionStart = 0;
        List<Integer> starts = new ArrayList<>();
        for (int i = sectionStart; i < sectionEnd; i++) {
            if (findListName(rows.get(i), imageWidth) != null) starts.add(i);
        }
        ArrayList<Holding> holdings = new ArrayList<>();
        for (int p = 0; p < starts.size(); p++) {
            int index = starts.get(p), next = p + 1 < starts.size() ? starts.get(p + 1) : sectionEnd;
            Row nameRow = rows.get(index);
            String company = findListName(nameRow, imageWidth);
            if (company == null) continue;
            Holding h = new Holding();
            h.name = new Field(company, "证券名称", "股票持仓列表该行名称 " + company, State.COLUMN_REVIEW);
            h.market = "A股";
            String code = findListCode(rows, index, Math.min(next, index + 2), imageWidth);
            if (!code.isEmpty()) h.code = new Field(code, "证券代码", "该持仓行显示的完整六位代码 " + code, State.COLUMN_REVIEW);
            else h.code = Field.missing("证券代码", "该列表截图未显示完整代码；请手工填写，不通过名称猜码");
            h.quantity = listQuantity(nameRow, imageWidth);
            h.cost = listCost(rows, index, next, imageWidth, imageHeight);
            if (h.quantity.value.isEmpty()) h.notices.add("持仓/可用列上行数量未能按列位明确匹配；数量留空。");
            if (h.cost.value.isEmpty()) h.notices.add("现价/成本列下行成本未能明确匹配；成本留空，不使用现价或持仓盈亏。");
            if (h.code.value.isEmpty()) h.notices.add("截图未显示该标的完整代码；需要手动填写，未进行代码推测。");
            h.notices.add("股票列表的名称、数量和成本均需在逐项核对后确认；没有自动导入交易或分红记录。");
            h.notices.add("首次建仓日期未从持仓列表识别；保存前需要手工选择有效日期。");
            holdings.add(h);
        }
        if (holdings.isEmpty()) return new Result(Layout.POSITION_LIST, Collections.singletonList(new Holding()), historyDetected,
                "识别到东方财富持仓列表表头，但没有可靠匹配到股票行；可继续手工修正。");
        return new Result(Layout.POSITION_LIST, holdings, historyDetected,
                "东方财富窄列持仓列表：名称、持仓/可用上行数量、现价/成本下行成本按列位分组；盈亏列不作数量/成本，基金分组不混入股票。截图未显示的代码和建仓日期留待手工确认。");
    }

    private Field listQuantity(Row row, int imageWidth) {
        List<String> inColumn = valuesInColumn(row, imageWidth, .80, 1.05);
        if (inColumn.size() == 1) return new Field(inColumn.get(0), "持仓数量", "持仓/可用列上行（持仓）", State.COLUMN_REVIEW);
        if (inColumn.size() > 1) {
            Set<String> unique = new HashSet<>(inColumn);
            if (unique.size() == 1) return new Field(inColumn.get(0), "持仓数量", "持仓/可用列上行（持仓）", State.COLUMN_REVIEW);
            return new Field("", "持仓数量", "持仓/可用列同一行出现多个数字 " + join(inColumn), State.AMBIGUOUS);
        }
        List<NumberToken> nums = numberTokens(row.flat);
        ArrayList<String> plain = new ArrayList<>(); for (NumberToken n : nums) if (!n.percent) plain.add(n.value);
        if (plain.size() >= 2) return new Field(plain.get(plain.size() - 1), "持仓数量", "持仓/可用列上行，按持仓列表从左至右列顺序匹配", State.COLUMN_REVIEW);
        return Field.missing("持仓数量", "未找到持仓/可用列上行数量");
    }

    private Field listCost(List<Row> rows, int index, int next, int imageWidth, int imageHeight) {
        int height = Math.max(1, imageHeight);
        int nameY = rows.get(index).centerY;
        for (int i = index + 1; i < next; i++) {
            Row row = rows.get(i);
            if (row.centerY - nameY > Math.max(48, height * .055)) break;
            List<String> inColumn = valuesInColumn(row, imageWidth, .54, .82);
            if (inColumn.size() == 1) return new Field(inColumn.get(0), "成本价", "现价/成本列下行（成本）", State.COLUMN_REVIEW);
            if (inColumn.size() > 1) {
                Set<String> unique = new HashSet<>(inColumn);
                if (unique.size() == 1) return new Field(inColumn.get(0), "成本价", "现价/成本列下行（成本）", State.COLUMN_REVIEW);
                return new Field("", "成本价", "现价/成本列下行出现多个候选 " + join(inColumn), State.AMBIGUOUS);
            }
            List<NumberToken> nums = numberTokens(row.flat);
            ArrayList<String> plain = new ArrayList<>(); for (NumberToken n : nums) if (!n.percent) plain.add(n.value);
            // A stacked holdings row is market value, P/L rate, cost, quantity.
            if (plain.size() >= 3 && nums.stream().anyMatch(n -> n.percent))
                return new Field(plain.get(plain.size() - 2), "成本价", "现价/成本列下行（百分比盈亏列之后、数量列之前）", State.COLUMN_REVIEW);
        }
        return Field.missing("成本价", "未找到该持仓行对应的现价/成本列下行值");
    }

    private String findListCode(List<Row> rows, int from, int to, int imageWidth) {
        for (int i = from; i < to; i++) {
            for (Fragment f : rows.get(i).fragments) {
                if (imageWidth > 0 && f.centerX() > imageWidth * .36) continue;
                Matcher m = SIX_DIGIT_CODE.matcher(f.text);
                if (m.find()) return m.group(1);
            }
        }
        return "";
    }

    private String findListName(Row row, int imageWidth) {
        StringBuilder grouped = new StringBuilder();
        int previousRight = -1;
        for (Fragment f : row.fragments) {
            if (imageWidth > 0 && f.centerX() > imageWidth * .30) continue;
            String han = f.text.replaceAll("[^\\u4e00-\\u9fff]", "");
            if (han.isEmpty()) continue;
            if (grouped.length() > 0 && f.left - previousRight > Math.max(32, f.height() * 1.8)) {
                String candidate = validName(grouped.toString());
                if (candidate != null) return candidate;
                grouped.setLength(0);
            }
            grouped.append(han);
            previousRight = f.right;
        }
        String combined = validName(grouped.toString());
        if (combined != null) return combined;
        for (Fragment f : row.fragments) {
            if (imageWidth > 0 && f.centerX() > imageWidth * .30) continue;
            String candidate = validName(f.text);
            if (candidate != null) return candidate;
        }
        Matcher start = Pattern.compile("^\\s*([\\u4e00-\\u9fff]{2,10})(?=\\s|$|[-—])").matcher(row.flat);
        if (start.find()) return validName(start.group(1));
        return null;
    }

    private String findHeaderName(List<Row> rows, String code, int imageWidth) {
        int codeRow = -1;
        if (!code.isEmpty()) for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).flat.replaceAll("\\s+", "").contains(code)) { codeRow = i; break; }
        }
        if (codeRow < 0 && !code.isEmpty()) return "";
        String first = codeRow >= 0 ? safeHeaderName(rows.get(codeRow), code, imageWidth) : "";
        if (codeRow >= 0) {
            Row anchor = rows.get(codeRow);
            int radius = Math.max(110, anchor.height * 4);
            for (int i = Math.max(0, codeRow - 1); i < Math.min(rows.size(), codeRow + 4); i++) {
                Row row = rows.get(i);
                if (Math.abs(row.centerY - anchor.centerY) > radius) continue;
                String nearby = safeHeaderName(row, "", imageWidth);
                if (!nearby.isEmpty() && nearby.toUpperCase(Locale.ROOT).contains("ETF")) return nearby;
            }
            return first;
        }
        for (Row row : rows) {
            String candidate = safeHeaderName(row, "", imageWidth);
            if (!candidate.isEmpty()) return candidate;
        }
        return "";
    }

    private String safeHeaderName(Row row, String code, int imageWidth) {
        if (!code.isEmpty()) {
            String adjacent = nameImmediatelyBeforeCode(row, code);
            if (!adjacent.isEmpty()) return adjacent;
        }
        for (Fragment fragment : row.fragments) {
            if (imageWidth > 0 && fragment.centerX() > imageWidth * .46) continue;
            if (!code.isEmpty() && fragment.text.contains(code)) {
                int at = fragment.text.indexOf(code);
                if (at > 0) {
                    String prefix = fragment.text.substring(0, at).replaceAll("[^\\u4e00-\\u9fffA-Za-z0-9·.&-]", "");
                    String candidate = validName(prefix);
                    if (candidate != null) return candidate;
                }
            }
            String candidate = validName(fragment.text);
            if (candidate != null) return candidate;
        }
        StringBuilder leftText = new StringBuilder();
        for (Fragment fragment : row.fragments) {
            if (imageWidth > 0 && fragment.centerX() > imageWidth * .46) continue;
            if (leftText.length() > 0) leftText.append(' ');
            leftText.append(fragment.text);
        }
        Matcher matcher = HAN_NAME.matcher(leftText.toString());
        while (matcher.find()) {
            String candidate = validName(matcher.group());
            if (candidate != null) return candidate;
        }
        return "";
    }

    private String nameImmediatelyBeforeCode(Row row, String code) {
        int codeIndex = -1;
        for (int i = 0; i < row.fragments.size(); i++) if (row.fragments.get(i).text.contains(code)) { codeIndex = i; break; }
        if (codeIndex <= 0) return "";
        StringBuilder name = new StringBuilder();
        int rightEdge = row.fragments.get(codeIndex).left;
        for (int i = codeIndex - 1; i >= 0; i--) {
            Fragment fragment = row.fragments.get(i);
            String han = fragment.text.replaceAll("[^\\u4e00-\\u9fffA-Za-z0-9·.-]", "");
            if (han.isEmpty()) break;
            int gap = rightEdge - fragment.right;
            if (gap > Math.max(32, fragment.height() * 1.8)) break;
            if (name.length() + han.length() > 12) break;
            name.insert(0, han);
            rightEdge = fragment.left;
        }
        String candidate = validName(name.toString());
        return candidate == null ? "" : candidate;
    }

    private String validName(String value) {
        String s = value == null ? "" : value.trim().replaceAll("\\s+", "");
        if (s.isEmpty() || s.length() > 16 || !s.matches("[\\u4e00-\\u9fffA-Za-z0-9·.&-]{2,16}") || NON_NAMES.contains(s)) return null;
        if (!s.matches(".*[\\u4e00-\\u9fffA-Za-z].*")) return null;
        for (String token : new String[]{"持仓", "盈亏", "数量", "成本", "现价", "可用", "股东", "交易", "市值", "当日", "历史", "红利", "收益", "资产", "参考", "记录", "买入", "卖出", "持有", "场内", "基金", "证券", "股份"})
            if (s.contains(token)) return null;
        return s;
    }

    private List<Field> labeledValues(List<Row> rows, String[] labels, String outputLabel) {
        ArrayList<Field> found = new ArrayList<>();
        for (Row row : rows) {
            String normalized = row.flat.replaceAll("\\s+", "");
            for (String label : labels) {
                int start = 0;
                while ((start = normalized.indexOf(label, start)) >= 0) {
                    int after = start + label.length();
                    int nextLabel = normalized.length();
                    for (String other : QUANTITY_LABELS) { int at = normalized.indexOf(other, after); if (at >= 0) nextLabel = Math.min(nextLabel, at); }
                    for (String other : COST_LABELS) { int at = normalized.indexOf(other, after); if (at >= 0) nextLabel = Math.min(nextLabel, at); }
                    for (String other : new String[]{"买入均价", "现价", "可用数量", "持仓盈亏", "当日参考盈亏", "持股天数", "个股仓位", "税费合计"}) { int at = normalized.indexOf(other, after); if (at >= 0) nextLabel = Math.min(nextLabel, at); }
                    String value = coordinateValueAfterLabel(row, label);
                    if (value.isEmpty()) {
                        Matcher m = NUMBER.matcher(normalized.substring(after, nextLabel));
                        if (m.find()) value = m.group(1).replace(",", "");
                    }
                    if (!value.isEmpty()) {
                        String safeEvidence = label + "：" + value + "（同一摘要行标签右侧数值）";
                        found.add(new Field(value, outputLabel, safeEvidence, State.LABELED_REVIEW));
                    }
                    start = after;
                }
            }
        }
        return found;
    }

    private String coordinateValueAfterLabel(Row row, String label) {
        Fragment anchor = null;
        for (Fragment fragment : row.fragments) {
            if (fragment.text.replaceAll("\\s+", "").contains(label)) { anchor = fragment; break; }
        }
        if (anchor == null) return "";
        int nextLabelX = Integer.MAX_VALUE;
        String[] boundaries = {"持仓数量", "持有数量", "持有股数", "股票余额", "证券数量", "可用数量", "持仓成本价", "成本价", "买入均价", "现价", "持仓盈亏", "当日参考盈亏", "持股天数", "个股仓位", "税费合计"};
        for (Fragment fragment : row.fragments) {
            if (fragment == anchor || fragment.left < anchor.right - Math.max(2, anchor.height() / 4)) continue;
            String text = fragment.text.replaceAll("\\s+", "");
            for (String boundary : boundaries) if (!boundary.equals(label) && text.contains(boundary)) nextLabelX = Math.min(nextLabelX, fragment.left);
        }
        Fragment nearest = null;
        for (Fragment fragment : row.fragments) {
            if (fragment == anchor || fragment.left < anchor.right - Math.max(2, anchor.height() / 4) || fragment.left >= nextLabelX) continue;
            if (fragment.text.replaceAll("\\s+", "").matches(".*(?:持仓数量|可用数量|成本价|买入均价|现价|盈亏|税费合计).*")) continue;
            if (numberTokens(fragment.text).isEmpty()) continue;
            if (nearest == null || fragment.left < nearest.left) nearest = fragment;
        }
        if (nearest == null) return "";
        List<NumberToken> tokens = numberTokens(nearest.text);
        return tokens.isEmpty() ? "" : tokens.get(0).value;
    }

    private Field resolveLabeled(List<Field> fields, String label) {
        if (fields.isEmpty()) return Field.missing(label, "未找到明确字段标签");
        LinkedHashMap<String, Field> unique = new LinkedHashMap<>();
        for (Field field : fields) unique.put(field.value, field);
        if (unique.size() > 1) return new Field("", label, "多个“" + label + "”标签给出不同候选，存在冲突、待核对，未选取任何值", State.AMBIGUOUS);
        return unique.values().iterator().next();
    }

    private List<String> valuesInColumn(Row row, int width, double low, double high) {
        ArrayList<String> values = new ArrayList<>();
        if (width <= 0) return values;
        for (Fragment f : row.fragments) {
            double x = (double) f.centerX() / width;
            if (x < low || x > high) continue;
            for (NumberToken token : numberTokens(f.text)) if (!token.percent) values.add(token.value);
        }
        return values;
    }

    private String marketFromCode(String code, String name) {
        if (code == null || code.isEmpty()) return "A股";
        if (code.matches("[0-9]{5}")) return "港股";
        String n = code.replaceAll("[^0-9]", "");
        String upperName = name == null ? "" : name.toUpperCase(Locale.ROOT);
        if (upperName.contains("ETF") || n.matches("(?:159|51|56|58)[0-9]{3}")) return "ETF";
        if (n.length() == 6) return "A股";
        return "A股";
    }

    private static final class NumberToken {
        final String value; final boolean percent;
        NumberToken(String value, boolean percent) { this.value = value.replace(",", ""); this.percent = percent; }
    }
    private List<NumberToken> numberTokens(String text) {
        ArrayList<NumberToken> result = new ArrayList<>();
        Matcher m = NUMBER.matcher(text == null ? "" : text);
        while (m.find()) result.add(new NumberToken(m.group(1), !m.group(2).isEmpty()));
        return result;
    }
    private String join(List<String> values) { return String.join(" / ", values); }

    private List<Row> groupRows(List<Fragment> input) {
        ArrayList<Fragment> fragments = new ArrayList<>();
        for (Fragment f : input) if (f != null && !f.text.isEmpty()) fragments.add(f);
        fragments.sort(Comparator.comparingInt(Fragment::centerY).thenComparingInt(f -> f.left));
        ArrayList<Row> rows = new ArrayList<>();
        for (Fragment f : fragments) {
            Row target = null;
            for (int i = rows.size() - 1; i >= 0; i--) {
                Row row = rows.get(i);
                if (f.centerY() - row.centerY > 24) break;
                int tolerance = Math.max(5, Math.min(15, (int) (Math.max(f.height(), row.height) * .55)));
                if (Math.abs(f.centerY() - row.centerY) <= tolerance) { target = row; break; }
            }
            if (target == null) { target = new Row(); rows.add(target); }
            target.add(f);
        }
        rows.sort(Comparator.comparingInt(r -> r.centerY));
        for (Row row : rows) row.finish();
        return rows;
    }

    private static final class Row {
        final List<Fragment> fragments = new ArrayList<>();
        int centerY, height;
        String flat = "";
        void add(Fragment f) {
            int count = fragments.size(); centerY = count == 0 ? f.centerY() : (centerY * count + f.centerY()) / (count + 1);
            height = Math.max(height, f.height()); fragments.add(f);
        }
        void finish() {
            fragments.sort(Comparator.comparingInt(f -> f.left));
            StringBuilder b = new StringBuilder();
            for (Fragment f : fragments) { if (b.length() > 0) b.append(' '); b.append(f.text); }
            flat = b.toString();
        }
    }
}
