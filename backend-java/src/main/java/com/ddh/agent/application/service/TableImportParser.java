package com.ddh.agent.application.service;

import com.ddh.agent.domain.model.table.TableColumn;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 表导入解析器（13 列标准模板）+ 模板生成。
 * 支持 .xlsx/.xls 与 .csv（引号感知）。表头按中文名定位，仅识别本模板。
 */
@Component
public class TableImportParser {

    /** 13 列标准表头（模板输出与解析匹配的单一来源） */
    public static final String[] HEADERS = {
        "字段序号", "字段名称", "字段中文名", "字段类型", "字段长度", "字段精度",
        "是否分布键", "是否分区建", "是否主键", "是否可为空", "代码信息", "缺省值", "下游作业数"
    };

    /** 模板示例行 */
    private static final String[] EXAMPLE_ROW = {
        "1", "user_id", "用户ID", "VARCHAR", "32", "", "是", "否", "是", "否", "", "", "0"
    };

    // ======================== 解析 ========================

    public List<TableColumn> parse(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        List<List<String>> rows;
        try {
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                rows = readExcel(file.getInputStream());
            } else {
                rows = readCsv(file.getInputStream());
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取文件失败");
        }
        return toColumns(rows);
    }

    private List<TableColumn> toColumns(List<List<String>> rows) {
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        // 表头 → 列索引
        Map<String, Integer> h = new HashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i) == null ? "" : header.get(i).trim();
            if (!key.isEmpty()) h.putIfAbsent(key, i);
        }
        int iSeq = idx(h, "字段序号");
        int iName = idx(h, "字段名称");
        int iCn = idx(h, "字段中文名");
        int iType = idx(h, "字段类型");
        int iLen = idx(h, "字段长度");
        int iPrec = idx(h, "字段精度");
        int iDist = idx(h, "是否分布键");
        int iPart = idx(h, "是否分区建", "是否分区键");
        int iPk = idx(h, "是否主键");
        int iNull = idx(h, "是否可为空");
        int iCode = idx(h, "代码信息");
        int iDef = idx(h, "缺省值");
        int iDown = idx(h, "下游作业数");

        if (iName < 0 || iType < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "模板表头缺少必填列：字段名称、字段类型");
        }

        List<TableColumn> result = new ArrayList<>();
        int order = 0;
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            String colName = cell(row, iName);
            if (colName == null || colName.trim().isEmpty()) continue; // 跳过空行

            TableColumn col = new TableColumn();
            col.setColumnName(colName.trim());
            col.setDataType(str(cell(row, iType)));
            col.setComment(emptyToNull(cell(row, iCn)));
            // 字段序号：优先模板值，缺失则按出现顺序
            Integer seq = parseInt(cell(row, iSeq));
            col.setSortOrder(seq != null ? seq : order);
            col.setColLength(parseInt(cell(row, iLen)));
            col.setColPrecision(parseInt(cell(row, iPrec)));
            col.setIsDistributionKey(parseBool(cell(row, iDist)));
            col.setIsPartitionKey(parseBool(cell(row, iPart)));
            col.setIsPrimaryKey(parseBool(cell(row, iPk)));
            col.setIsNullable(parseBool(cell(row, iNull)));
            col.setCodeInfo(emptyToNull(cell(row, iCode)));
            col.setDefaultValue(emptyToNull(cell(row, iDef)));
            col.setDownstreamJobCount(parseInt(cell(row, iDown)));
            result.add(col);
            order++;
        }
        if (result.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未解析到任何字段行");
        }
        return result;
    }

    // ======================== 模板生成 ========================

    /** 生成模板（含表头 + 一行示例）。format = "xlsx" 或 "csv"。 */
    public byte[] buildTemplate(String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return buildCsvTemplate();
        }
        return buildXlsxTemplate();
    }

    private byte[] buildCsvTemplate() {
        StringBuilder sb = new StringBuilder();
        sb.append((char) 0xFEFF); // UTF-8 BOM，防止 Excel 打开中文乱码
        sb.append(joinCsv(HEADERS)).append("\r\n");
        sb.append(joinCsv(EXAMPLE_ROW)).append("\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildXlsxTemplate() {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("字段定义");
            CellStyle headStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headStyle.setFont(bold);
            Row head = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = head.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headStyle);
                sheet.setColumnWidth(i, 14 * 256);
            }
            Row ex = sheet.createRow(1);
            for (int i = 0; i < EXAMPLE_ROW.length; i++) {
                ex.createCell(i).setCellValue(EXAMPLE_ROW[i]);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "生成模板失败");
        }
    }

    // ======================== Excel / CSV 读取 ========================

    private List<List<String>> readExcel(InputStream is) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return rows;
            DataFormatter fmt = new DataFormatter();
            int lastRow = sheet.getLastRowNum();
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    int lastCell = row.getLastCellNum();
                    for (int c = 0; c < lastCell; c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : cellToString(cell, fmt));
                    }
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    private String cellToString(Cell cell, DataFormatter fmt) {
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double d = cell.getNumericCellValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d); // 整数避免出现 "20.0"
            }
            return String.valueOf(d);
        }
        return fmt.formatCellValue(cell).trim();
    }

    private List<List<String>> readCsv(InputStream is) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    if (!line.isEmpty() && line.charAt(0) == '﻿') line = line.substring(1); // 去 BOM
                    first = false;
                }
                if (line.isEmpty()) {
                    rows.add(Collections.emptyList());
                    continue;
                }
                rows.add(parseCsvLine(line));
            }
        }
        return rows;
    }

    /** 引号感知的 CSV 行解析：支持 "..." 包裹、字段内逗号、"" 转义引号。 */
    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
        }
        out.add(cur.toString());
        return out;
    }

    // ======================== 工具 ========================

    private int idx(Map<String, Integer> h, String... names) {
        for (String n : names) {
            Integer i = h.get(n);
            if (i != null) return i;
        }
        return -1;
    }

    private String cell(List<String> row, int idx) {
        if (idx < 0 || row == null || idx >= row.size()) return null;
        return row.get(idx);
    }

    private String str(String s) {
        return s == null ? "" : s.trim();
    }

    private String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Integer parseInt(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            // 容错：去掉可能的小数 ".0"
            if (t.endsWith(".0")) t = t.substring(0, t.length() - 2);
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 是/否容错解析 → 0/1。空 → 0。 */
    private Integer parseBool(String s) {
        if (s == null) return 0;
        String t = s.trim().toLowerCase();
        if (t.isEmpty()) return 0;
        switch (t) {
            case "是": case "y": case "yes": case "true": case "1":
            case "√": case "✓": case "t":
                return 1;
            default:
                return 0;
        }
    }

    private String joinCsv(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            String f = fields[i] == null ? "" : fields[i];
            if (f.contains(",") || f.contains("\"") || f.contains("\n")) {
                sb.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(f);
            }
        }
        return sb.toString();
    }
}
