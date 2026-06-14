package com.ddh.agent.application.service;

import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.interfaces.dto.request.TableUpdateRequest;
import com.ddh.agent.interfaces.dto.response.*;
import com.ddh.agent.application.assembler.TableAssembler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TableAppService {

    @Autowired private SourceTableRepository sourceTableRepository;
    @Autowired private TableAssembler assembler;

    public TableResponse importCsv(MultipartFile file, Integer scope,
                                   String description, Long currentUserId) {
        String filename = file.getOriginalFilename() != null
            ? file.getOriginalFilename() : "unknown.csv";
        String tableName = filename.contains(".")
            ? filename.substring(0, filename.lastIndexOf('.')) : filename;

        List<TableColumn> columns;
        try {
            columns = parseCsv(file.getInputStream());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read file");
        }

        SourceTable table = new SourceTable();
        table.setName(tableName);
        table.setDescription(description);
        table.setScope(scope);
        table.setOwnerId(scope != null && scope == 1 ? null : currentUserId);
        table.setCreatedAt(LocalDateTime.now());
        sourceTableRepository.save(table);

        int i = 0;
        for (TableColumn col : columns) {
            col.setTableId(table.getId());
            col.setSortOrder(i++);
        }
        sourceTableRepository.saveColumns(columns);
        return assembler.toResponse(table);
    }

    /** CSV 需含 column_name、data_type 列，comment 可选 */
    private List<TableColumn> parseCsv(InputStream is) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is empty");
            }
            List<String> headers = Arrays.stream(headerLine.split(","))
                .map(String::trim).collect(Collectors.toList());
            int colNameIdx = headers.indexOf("column_name");
            int dataTypeIdx = headers.indexOf("data_type");
            int commentIdx = headers.indexOf("comment");
            if (colNameIdx < 0 || dataTypeIdx < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "CSV missing required columns: column_name, data_type");
            }
            List<TableColumn> result = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                TableColumn col = new TableColumn();
                col.setColumnName(parts[colNameIdx].trim());
                col.setDataType(parts[dataTypeIdx].trim());
                if (commentIdx >= 0 && commentIdx < parts.length) {
                    String c = parts[commentIdx].trim();
                    col.setComment(c.isEmpty() ? null : c);
                }
                result.add(col);
            }
            return result;
        }
    }

    public List<TableResponse> listTables(String scope, Long currentUserId) {
        return sourceTableRepository.findVisible(currentUserId).stream()
            .filter(t -> {
                if ("public".equals(scope)) return t.getScope() != null && t.getScope() == 1;
                if ("private".equals(scope)) return t.getScope() != null && t.getScope() == 2
                    && currentUserId.equals(t.getOwnerId());
                return true;
            })
            .map(assembler::toResponse)
            .collect(Collectors.toList());
    }

    public TableDetailResponse getTable(Long tableId) {
        SourceTable table = sourceTableRepository.findById(tableId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Table not found"));
        List<TableColumn> cols = sourceTableRepository.findColumnsByTableId(tableId);
        return assembler.toDetailResponseFromTable(table, cols);
    }

    public TableResponse updateTable(Long tableId, TableUpdateRequest req) {
        SourceTable table = sourceTableRepository.findById(tableId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Table not found"));
        if (req.getName() != null) table.setName(req.getName());
        if (req.getDescription() != null) table.setDescription(req.getDescription());
        sourceTableRepository.save(table);
        return assembler.toResponse(table);
    }

    public void deleteTable(Long tableId) {
        sourceTableRepository.findById(tableId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Table not found"));
        sourceTableRepository.deleteColumnsByTableId(tableId);
        sourceTableRepository.deleteById(tableId);
    }
}