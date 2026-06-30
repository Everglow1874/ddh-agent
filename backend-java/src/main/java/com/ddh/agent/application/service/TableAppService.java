package com.ddh.agent.application.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.interfaces.dto.request.ColumnCreateRequest;
import com.ddh.agent.interfaces.dto.request.ColumnUpdateRequest;
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
    @Autowired private TableImportParser importParser;

    /** 导入表：支持 13 列标准模板的 .xlsx / .xls / .csv */
    public TableResponse importTable(MultipartFile file, Integer scope,
                                     String description, Long currentUserId) {
        String filename = file.getOriginalFilename() != null
            ? file.getOriginalFilename() : "unknown";
        String tableName = filename.contains(".")
            ? filename.substring(0, filename.lastIndexOf('.')) : filename;

        List<TableColumn> columns = importParser.parse(file);

        SourceTable table = new SourceTable();
        table.setName(tableName);
        table.setDescription(description);
        table.setScope(scope);
        table.setOwnerId(scope != null && scope == 1 ? null : currentUserId);
        table.setCreatedAt(LocalDateTime.now());
        sourceTableRepository.save(table);

        for (TableColumn col : columns) {
            col.setTableId(table.getId());
        }
        sourceTableRepository.saveColumns(columns);
        return assembler.toResponse(table);
    }

    /** 生成导入模板（xlsx / csv） */
    public byte[] buildTemplate(String format) {
        return importParser.buildTemplate(format);
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

    public PageResponse<TableResponse> listTablesPage(String search, String scope, Long currentUserId, int page, int size) {
        Integer scopeVal = "public".equals(scope) ? 1 : "private".equals(scope) ? 2 : null;
        IPage<SourceTable> p = sourceTableRepository.findPageable(search, scopeVal, currentUserId, page, size);
        return PageResponse.of(p,
            p.getRecords().stream().map(assembler::toResponse).collect(Collectors.toList()));
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

    public void addColumn(Long tableId, ColumnCreateRequest req) {
        sourceTableRepository.findById(tableId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
        List<TableColumn> existing = sourceTableRepository.findColumnsByTableId(tableId);
        int nextOrder = existing.stream().mapToInt(TableColumn::getSortOrder).max().orElse(-1) + 1;
        TableColumn col = new TableColumn();
        col.setTableId(tableId);
        col.setColumnName(req.getColumnName());
        col.setDataType(req.getDataType());
        col.setComment(req.getComment());
        col.setSortOrder(nextOrder);
        col.setColLength(req.getColLength());
        col.setColPrecision(req.getColPrecision());
        col.setIsDistributionKey(req.getIsDistributionKey());
        col.setIsPartitionKey(req.getIsPartitionKey());
        col.setIsPrimaryKey(req.getIsPrimaryKey());
        col.setIsNullable(req.getIsNullable());
        col.setCodeInfo(req.getCodeInfo());
        col.setDefaultValue(req.getDefaultValue());
        col.setDownstreamJobCount(req.getDownstreamJobCount());
        sourceTableRepository.saveColumn(col);
    }

    public void updateColumn(Long tableId, Long columnId, ColumnUpdateRequest req) {
        sourceTableRepository.findById(tableId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
        TableColumn col = sourceTableRepository.findColumnById(columnId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Column not found"));
        if (!tableId.equals(col.getTableId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column does not belong to this table");
        }
        if (req.getColumnName() != null) col.setColumnName(req.getColumnName());
        if (req.getDataType() != null) col.setDataType(req.getDataType());
        if (req.getComment() != null) col.setComment(req.getComment());
        if (req.getSortOrder() != null) col.setSortOrder(req.getSortOrder());
        if (req.getColLength() != null) col.setColLength(req.getColLength());
        if (req.getColPrecision() != null) col.setColPrecision(req.getColPrecision());
        if (req.getIsDistributionKey() != null) col.setIsDistributionKey(req.getIsDistributionKey());
        if (req.getIsPartitionKey() != null) col.setIsPartitionKey(req.getIsPartitionKey());
        if (req.getIsPrimaryKey() != null) col.setIsPrimaryKey(req.getIsPrimaryKey());
        if (req.getIsNullable() != null) col.setIsNullable(req.getIsNullable());
        if (req.getCodeInfo() != null) col.setCodeInfo(req.getCodeInfo());
        if (req.getDefaultValue() != null) col.setDefaultValue(req.getDefaultValue());
        if (req.getDownstreamJobCount() != null) col.setDownstreamJobCount(req.getDownstreamJobCount());
        sourceTableRepository.updateColumn(col);
    }

    public void deleteColumn(Long tableId, Long columnId) {
        sourceTableRepository.findById(tableId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
        TableColumn col = sourceTableRepository.findColumnById(columnId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Column not found"));
        if (!tableId.equals(col.getTableId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Column does not belong to this table");
        }
        sourceTableRepository.deleteColumnById(columnId);
    }

    public void deleteTable(Long tableId) {
        sourceTableRepository.findById(tableId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Table not found"));
        sourceTableRepository.deleteColumnsByTableId(tableId);
        sourceTableRepository.deleteById(tableId);
    }
}