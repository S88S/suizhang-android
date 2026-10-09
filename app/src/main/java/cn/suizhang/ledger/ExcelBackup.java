package cn.suizhang.ledger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Builds and reads an editable, multi-sheet Excel workbook without uploading ledger data. */
final class ExcelBackup {
    static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int MAX_FILE_BYTES = 10_000_000;
    private static final int MAX_ARCHIVE_BYTES = 20 * 1024 * 1024;
    private static final int MAX_ENTRIES = 100;
    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private static final class Field {
        final String key;
        final String label;
        Field(String key, String label) { this.key = key; this.label = label; }
    }

    private static final class Sheet {
        final String name;
        final String arrayName;
        final Field[] fields;
        Sheet(String name, String arrayName, Field... fields) {
            this.name = name; this.arrayName = arrayName; this.fields = fields;
        }
    }

    private static Field f(String key, String label) { return new Field(key, label); }

    private static final List<Sheet> DATA_SHEETS = Collections.unmodifiableList(Arrays.asList(
            new Sheet("账户", "accounts",
                    f("_id", "账户ID（勿改）"), f("name", "账户名称"), f("created_at", "创建时间")),
            new Sheet("持仓", "holdings",
                    f("_id", "持仓ID（勿改）"), f("account_id", "所属账户ID（勿改）"),
                    f("name", "标的名称"), f("code", "证券代码"), f("market", "市场"), f("currency", "币种"),
                    f("quantity", "当前持有数量"), f("cost", "每份持仓成本"),
                    f("annual_dividend_per_unit", "每份年分红"), f("tax_rate", "税率（0–1）"),
                    f("opened_on", "建仓日期"), f("initial_quantity", "初始持仓数量"),
                    f("tax_mode", "税务模式"), f("cost_method", "成本算法"), f("created_at", "创建时间")),
            new Sheet("分红", "dividends",
                    f("_id", "分红ID（勿改）"), f("holding_id", "持仓ID（勿改）"), f("account_id", "账户ID（勿改）"),
                    f("amount_per_share", "每份分红"), f("total", "分红总额"), f("currency", "币种"),
                    f("record_date", "登记日"), f("ex_date", "除息日"), f("pay_date", "派息日"),
                    f("status", "状态（expected/received）"), f("note", "备注"), f("created_at", "创建时间"),
                    f("source", "来源"), f("source_key", "来源标识"), f("data_class", "数据类别"),
                    f("record_quantity", "登记日持股数量"), f("pay_date_estimated", "派息日为估算（0/1）"),
                    f("tax_rate", "税率（0–1）"), f("tax_known", "税率已确认（0/1）"), f("received_amount", "实际到账金额")),
            new Sheet("交易", "transactions",
                    f("_id", "交易ID（勿改）"), f("holding_id", "持仓ID（勿改）"), f("account_id", "账户ID（勿改）"),
                    f("side", "方向（买入/卖出）"), f("trade_date", "交易日期"), f("quantity", "数量"),
                    f("price", "成交单价"), f("fees", "手续费"), f("note", "备注"), f("created_at", "创建时间")),
            new Sheet("支出目标", "expense_goals",
                    f("_id", "目标ID（勿改）"), f("name", "目标名称"), f("period", "周期（日/月/年）"),
                    f("amount", "金额"), f("currency", "币种"), f("created_at", "创建时间")),
            new Sheet("标的索引", "asset_index",
                    f("_id", "索引ID（勿改）"), f("code", "证券代码"), f("name", "标的名称"), f("market", "市场")),
            new Sheet("本地设置", "app_settings",
                    f("key", "设置键"), f("value", "设置值"))
    ));

    private static final Map<String, Sheet> SHEETS_BY_NAME;
    private static final Map<String, String> LABEL_TO_FIELD;
    private static final Map<String, String> DATE_FIELDS;
    private static final Map<String, String> TEXT_FIELDS;
    private static final Map<String, String> INTEGER_FIELDS;

    static {
        Map<String, Sheet> byName = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (Sheet sheet : DATA_SHEETS) {
            byName.put(sheet.name, sheet);
            for (Field field : sheet.fields) {
                labels.put(sheet.name + "\u0000" + field.label, field.key);
                labels.put(sheet.name + "\u0000" + field.key, field.key);
            }
        }
        SHEETS_BY_NAME = Collections.unmodifiableMap(byName);
        LABEL_TO_FIELD = Collections.unmodifiableMap(labels);
        Map<String, String> dates = new HashMap<>();
        for (String key : new String[]{"opened_on", "record_date", "ex_date", "pay_date", "trade_date"}) dates.put(key, key);
        DATE_FIELDS = Collections.unmodifiableMap(dates);
        Map<String, String> text = new HashMap<>();
        for (String key : new String[]{"name", "created_at", "code", "market", "currency", "opened_on", "tax_mode", "cost_method", "record_date", "ex_date", "pay_date", "status", "note", "source", "source_key", "data_class", "side", "trade_date", "period", "key", "value"}) text.put(key, key);
        TEXT_FIELDS = Collections.unmodifiableMap(text);
        Map<String, String> integers = new HashMap<>();
        for (String key : new String[]{"_id", "account_id", "holding_id", "pay_date_estimated", "tax_known"}) integers.put(key, key);
        INTEGER_FIELDS = Collections.unmodifiableMap(integers);
    }

    private ExcelBackup() { }

    static byte[] exportWorkbook(String backupJson) throws Exception {
        JSONObject root = new JSONObject(backupJson);
        ArrayList<String> sheetNames = new ArrayList<>();
        sheetNames.add("使用说明");
        for (Sheet sheet : DATA_SHEETS) sheetNames.add(sheet.name);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypes(sheetNames.size()));
            put(zip, "_rels/.rels", ROOT_RELS);
            put(zip, "xl/workbook.xml", workbookXml(sheetNames));
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheetNames.size()));
            put(zip, "xl/styles.xml", STYLES);
            put(zip, "xl/worksheets/sheet1.xml", instructionsSheet());
            for (int i = 0; i < DATA_SHEETS.size(); i++) {
                Sheet sheet = DATA_SHEETS.get(i);
                JSONArray records = root.getJSONArray(sheet.arrayName);
                put(zip, "xl/worksheets/sheet" + (i + 2) + ".xml", dataSheet(sheet, records));
            }
        }
        if (bytes.size() > MAX_FILE_BYTES) throw new JSONException("Excel 备份超过 10 MB，请减少账本历史数据后重试");
        return bytes.toByteArray();
    }

    static String importWorkbook(byte[] workbook) throws Exception {
        if (workbook == null || workbook.length < 4 || workbook.length > MAX_FILE_BYTES) {
            throw new JSONException("Excel 文件为空或超过 10 MB");
        }
        Map<String, byte[]> entries = unzip(workbook);
        byte[] workbookXml = entries.get("xl/workbook.xml");
        byte[] relsXml = entries.get("xl/_rels/workbook.xml.rels");
        if (workbookXml == null || relsXml == null) throw new JSONException("不是有效的 .xlsx 工作簿");
        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        Map<String, String> relationships = parseRelationships(relsXml);
        Map<String, String> sheets = parseWorkbookSheets(workbookXml, relationships);

        JSONObject root = new JSONObject();
        root.put("app", "穗账");
        root.put("schema_version", 4);
        root.put("exported_at", Instant.now().toString());
        for (Sheet definition : DATA_SHEETS) {
            String path = sheets.get(definition.name);
            if (path == null) throw new JSONException("工作簿缺少工作表：" + definition.name);
            byte[] xml = entries.get(path);
            if (xml == null) throw new JSONException("无法读取工作表：" + definition.name);
            root.put(definition.arrayName, parseDataSheet(definition, xml, sharedStrings));
        }
        return root.toString();
    }

    private static JSONArray parseDataSheet(Sheet sheet, byte[] xml, List<String> sharedStrings) throws Exception {
        List<Map<Integer, String>> rows = parseRows(xml, sharedStrings);
        if (rows.isEmpty()) throw new JSONException("工作表没有字段标题：" + sheet.name);
        Map<Integer, String> header = rows.get(0);
        Map<String, Integer> fieldColumns = new HashMap<>();
        for (Map.Entry<Integer, String> cell : header.entrySet()) {
            String label = cell.getValue() == null ? "" : cell.getValue().trim();
            String key = LABEL_TO_FIELD.get(sheet.name + "\u0000" + label);
            if (key != null) {
                if (fieldColumns.containsKey(key)) throw new JSONException(sheet.name + "工作表字段重复：" + label);
                fieldColumns.put(key, cell.getKey());
            }
        }
        for (Field field : sheet.fields) {
            if (!fieldColumns.containsKey(field.key)) throw new JSONException(sheet.name + "工作表缺少字段：" + field.label);
        }

        JSONArray result = new JSONArray();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            Map<Integer, String> row = rows.get(rowIndex);
            boolean empty = true;
            for (String value : row.values()) if (value != null && !value.trim().isEmpty()) { empty = false; break; }
            if (empty) continue;
            JSONObject record = new JSONObject();
            for (Field field : sheet.fields) {
                String raw = row.get(fieldColumns.get(field.key));
                Object value = parseFieldValue(sheet.name, field.key, raw);
                record.put(field.key, value);
            }
            result.put(record);
        }
        return result;
    }

    private static Object parseFieldValue(String sheetName, String key, String raw) throws JSONException {
        if (raw == null || raw.trim().isEmpty()) return JSONObject.NULL;
        String value = raw.trim();
        try {
            if (TEXT_FIELDS.containsKey(key)) {
                if (DATE_FIELDS.containsKey(key) && isNumber(value)) return excelDate(value);
                return raw;
            }
            if (INTEGER_FIELDS.containsKey(key)) return new BigDecimal(value.replace(",", "")).longValueExact();
            double number = Double.parseDouble(value.replace(",", ""));
            if (!Double.isFinite(number)) throw new NumberFormatException("not finite");
            return number;
        } catch (Exception e) {
            throw new JSONException(sheetName + "工作表中的“" + key + "”不是有效的" + (INTEGER_FIELDS.containsKey(key) ? "整数" : "数值") + "：" + value);
        }
    }

    private static String excelDate(String serial) {
        BigDecimal decimal = new BigDecimal(serial.replace(",", ""));
        long days = decimal.longValue();
        return LocalDate.of(1899, 12, 30).plusDays(days).toString();
    }

    private static boolean isNumber(String value) {
        try { Double.parseDouble(value.replace(",", "")); return true; }
        catch (NumberFormatException ignored) { return false; }
    }

    private static List<Map<Integer, String>> parseRows(byte[] xml, List<String> sharedStrings) throws Exception {
        XmlPullParser parser = parser(xml);
        ArrayList<Map<Integer, String>> rows = new ArrayList<>();
        Map<Integer, String> row = null;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "row".equals(parser.getName())) {
                row = new LinkedHashMap<>();
            } else if (event == XmlPullParser.START_TAG && "c".equals(parser.getName()) && row != null) {
                String reference = parser.getAttributeValue(null, "r");
                String type = parser.getAttributeValue(null, "t");
                int column = columnIndex(reference);
                String value = parseCell(parser, type, sharedStrings);
                if (column >= 0) row.put(column, value);
            } else if (event == XmlPullParser.END_TAG && "row".equals(parser.getName()) && row != null) {
                rows.add(row); row = null;
                if (rows.size() > 100_000) throw new JSONException("Excel 工作表行数过多");
            }
        }
        return rows;
    }

    private static String parseCell(XmlPullParser parser, String type, List<String> sharedStrings) throws Exception {
        String value = "";
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("f".equals(name)) throw new JSONException("为安全起见，不导入含公式的工作簿；请先将公式转换为数值");
                if ("v".equals(name) || "t".equals(name)) {
                    String text = parser.nextText();
                    if (value.isEmpty() || "v".equals(name)) value = text;
                }
            } else if (event == XmlPullParser.END_TAG && "c".equals(parser.getName())) {
                break;
            }
        }
        if ("s".equals(type) && !value.isEmpty()) {
            int index;
            try { index = Integer.parseInt(value); }
            catch (NumberFormatException e) { throw new JSONException("Excel 共享文本索引无效"); }
            if (index < 0 || index >= sharedStrings.size()) throw new JSONException("Excel 共享文本索引越界");
            return sharedStrings.get(index);
        }
        if ("b".equals(type)) return "1".equals(value) ? "1" : "0";
        return value;
    }

    private static List<String> parseSharedStrings(byte[] xml) throws Exception {
        if (xml == null) return Collections.emptyList();
        XmlPullParser parser = parser(xml);
        ArrayList<String> strings = new ArrayList<>();
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "si".equals(parser.getName())) {
                StringBuilder value = new StringBuilder();
                readRichText(parser, "si", value);
                strings.add(value.toString());
                if (strings.size() > 1_000_000) throw new JSONException("Excel 共享文本数量过多");
            }
        }
        return strings;
    }

    private static void readRichText(XmlPullParser parser, String endTag, StringBuilder value) throws Exception {
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "t".equals(parser.getName())) value.append(parser.nextText());
            else if (event == XmlPullParser.END_TAG && endTag.equals(parser.getName())) return;
        }
        throw new JSONException("Excel XML 结构不完整");
    }

    private static Map<String, String> parseRelationships(byte[] xml) throws Exception {
        XmlPullParser parser = parser(xml);
        HashMap<String, String> result = new HashMap<>();
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "Relationship".equals(parser.getName())) {
                String id = parser.getAttributeValue(null, "Id");
                String target = parser.getAttributeValue(null, "Target");
                String mode = parser.getAttributeValue(null, "TargetMode");
                if (id != null && target != null && !"External".equalsIgnoreCase(mode)) result.put(id, normalizeWorkbookTarget(target));
            }
        }
        return result;
    }

    private static String normalizeWorkbookTarget(String target) throws JSONException {
        String source = target.replace('\\', '/');
        if (source.startsWith("/xl/")) source = source.substring(1);
        else if (source.startsWith("/") || source.contains("://")) throw new JSONException("工作簿包含不支持的外部路径");
        String relative = source.startsWith("xl/") ? source : "xl/" + source;
        ArrayList<String> parts = new ArrayList<>();
        for (String part : relative.split("/")) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) {
                if (parts.isEmpty()) throw new JSONException("工作簿路径无效");
                parts.remove(parts.size() - 1);
            } else parts.add(part);
        }
        return join(parts, "/");
    }

    private static Map<String, String> parseWorkbookSheets(byte[] xml, Map<String, String> relationships) throws Exception {
        XmlPullParser parser = parser(xml);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "sheet".equals(parser.getName())) {
                String name = parser.getAttributeValue(null, "name");
                String relation = parser.getAttributeValue(REL_NS, "id");
                if (relation == null) relation = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                String path = relationships.get(relation);
                if (name != null && path != null) result.put(name, path);
            }
        }
        return result;
    }

    private static int columnIndex(String reference) {
        if (reference == null) return -1;
        int result = 0, count = 0;
        for (int i = 0; i < reference.length(); i++) {
            char ch = reference.charAt(i);
            if (ch >= 'A' && ch <= 'Z') { result = result * 26 + ch - 'A' + 1; count++; }
            else if (ch >= 'a' && ch <= 'z') { result = result * 26 + ch - 'a' + 1; count++; }
            else break;
        }
        return count == 0 ? -1 : result - 1;
    }

    private static XmlPullParser parser(byte[] xml) throws XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        return parser;
    }

    private static Map<String, byte[]> unzip(byte[] workbook) throws IOException, JSONException {
        LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
        int total = 0, count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(workbook), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) throw new JSONException("Excel 文件包含过多项目");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.equals("..")) throw new JSONException("Excel 文件路径无效");
                if (entry.isDirectory()) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_ARCHIVE_BYTES) throw new JSONException("Excel 解压后超过 20 MB");
                    out.write(buffer, 0, n);
                }
                files.put(name, out.toByteArray());
            }
        }
        return files;
    }

    private static String dataSheet(Sheet sheet, JSONArray records) throws Exception {
        ArrayList<List<Object>> rows = new ArrayList<>();
        ArrayList<Object> headers = new ArrayList<>();
        for (Field field : sheet.fields) headers.add(field.label);
        rows.add(headers);
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            ArrayList<Object> values = new ArrayList<>();
            for (Field field : sheet.fields) values.add(record.opt(field.key));
            rows.add(values);
        }
        return worksheetXml(rows, true);
    }

    private static String instructionsSheet() throws Exception {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(Arrays.<Object>asList("项目", "说明"));
        rows.add(Arrays.<Object>asList("格式", "这是穗账完整备份工作簿，格式为标准 .xlsx，可用 Microsoft Excel、WPS 表格或 LibreOffice 打开。"));
        rows.add(Arrays.<Object>asList("工作表", "账户、持仓、分红、交易、支出目标、标的索引和本地设置分别对应账本数据。"));
        rows.add(Arrays.<Object>asList("修改", "可编辑单元格内容、排序和筛选；请保留工作表名称、字段标题和全部工作表。空白行会忽略。"));
        rows.add(Arrays.<Object>asList("关联编号", "账户ID、持仓ID及各类ID用于关联记录。请勿编辑、删除、复制这些编号列，否则记录可能无法恢复。"));
        rows.add(Arrays.<Object>asList("导入", "导入会替换本机全部账户和账本数据。建议先另存一份导出文件；穗账会在确认后验证关联与数据，验证失败时保留原账本。"));
        rows.add(Arrays.<Object>asList("隐私", "文件仅保存在你选择的位置；穗账不会自动上传。请妥善保管含个人财务信息的文件。"));
        rows.add(Arrays.<Object>asList("兼容", "可导入穗账导出的 Excel 工作簿；旧版 JSON 备份仍可在应用内导入。公式工作簿会被拒绝，请先将公式转换成数值。"));
        return worksheetXml(rows, false);
    }

    private static String worksheetXml(List<List<Object>> rows, boolean freezeHeader) throws Exception {
        int columns = 0;
        for (List<Object> row : rows) columns = Math.max(columns, row.size());
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"").append(MAIN_NS).append("\"><sheetViews><sheetView workbookViewId=\"0\" showGridLines=\"0\">");
        if (freezeHeader) xml.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/><selection pane=\"bottomLeft\" activeCell=\"A2\" sqref=\"A2\"/>");
        xml.append("</sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"21\"/><cols>");
        for (int c = 0; c < columns; c++) {
            int width = 17;
            if (!rows.isEmpty() && c < rows.get(0).size()) width = Math.max(13, Math.min(28, String.valueOf(rows.get(0).get(c)).length() * 2 + 3));
            xml.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            List<Object> row = rows.get(r);
            xml.append("<row r=\"").append(r + 1).append("\" ht=\"").append(r == 0 ? 25 : 21).append("\" customHeight=\"1\">");
            for (int c = 0; c < row.size(); c++) writeCell(xml, row.get(c), c, r, r == 0);
            xml.append("</row>");
        }
        xml.append("</sheetData>");
        if (freezeHeader && columns > 0 && !rows.isEmpty()) xml.append("<autoFilter ref=\"A1:").append(columnName(columns - 1)).append(rows.size()).append("\"/>");
        xml.append("<pageMargins left=\"0.25\" right=\"0.25\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/></worksheet>");
        return xml.toString();
    }

    private static void writeCell(StringBuilder xml, Object value, int column, int row, boolean header) throws JSONException {
        String ref = columnName(column) + (row + 1);
        if (value == null || value == JSONObject.NULL) {
            xml.append("<c r=\"").append(ref).append('"');
            if (header) xml.append(" s=\"1\"");
            xml.append("/>");
            return;
        }
        xml.append("<c r=\"").append(ref).append('"');
        if (header) xml.append(" s=\"1\"");
        if (value instanceof String || value instanceof Character) {
            String text = String.valueOf(value);
            if (text.length() > 32767) throw new JSONException("单元格文字超过 Excel 允许长度");
            xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xmlEscape(text)).append("</t></is></c>");
        } else if (value instanceof Boolean) {
            xml.append(" t=\"b\"><v>").append((Boolean) value ? "1" : "0").append("</v></c>");
        } else if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) throw new JSONException("备份包含无效数值");
            xml.append("><v>").append(value.toString()).append("</v></c>");
        } else {
            xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xmlEscape(String.valueOf(value))).append("</t></is></c>");
        }
    }

    private static String workbookXml(List<String> names) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"").append(MAIN_NS)
                .append("\" xmlns:r=\"").append(REL_NS).append("\"><bookViews><workbookView/></bookViews><sheets>");
        for (int i = 0; i < names.size(); i++) xml.append("<sheet name=\"").append(xmlEscape(names.get(i))).append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        return xml.append("</sheets><calcPr calcId=\"191029\"/></workbook>").toString();
    }

    private static String workbookRels(int sheetCount) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheetCount; i++) xml.append("<Relationship Id=\"rId").append(i + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
        xml.append("<Relationship Id=\"rId").append(sheetCount + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
        return xml.toString();
    }

    private static String contentTypes(int sheetCount) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= sheetCount; i++) xml.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        return xml.append("</Types>").toString();
    }

    private static String columnName(int column) {
        StringBuilder out = new StringBuilder();
        int n = column + 1;
        while (n > 0) { int rem = (n - 1) % 26; out.insert(0, (char) ('A' + rem)); n = (n - 1) / 26; }
        return out.toString();
    }

    private static String xmlEscape(String input) {
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int offset = 0; offset < input.length();) {
            int cp = input.codePointAt(offset); offset += Character.charCount(cp);
            if (!(cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF))) continue;
            switch (cp) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&apos;"); break;
                default: out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String join(List<String> parts, String separator) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) { if (out.length() > 0) out.append(separator); out.append(part); }
        return out.toString();
    }

    private static final String ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>";
    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><color theme=\"1\"/><name val=\"Aptos\"/></font><font><b/><sz val=\"11\"/><color rgb=\"FF2E271A\"/><name val=\"Aptos\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE7D8B5\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";
}
