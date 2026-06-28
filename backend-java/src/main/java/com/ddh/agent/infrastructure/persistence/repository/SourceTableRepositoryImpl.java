package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.infrastructure.persistence.mapper.SourceTableMapper;
import com.ddh.agent.infrastructure.persistence.mapper.TableColumnMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import java.util.*;

@Repository
public class SourceTableRepositoryImpl implements SourceTableRepository {

    @Autowired private SourceTableMapper sourceTableMapper;
    @Autowired private TableColumnMapper tableColumnMapper;

    @Override
    public Optional<SourceTable> findById(Long id) {
        return Optional.ofNullable(sourceTableMapper.selectById(id));
    }

    @Override
    public List<SourceTable> findVisible(Long currentUserId) {
        return sourceTableMapper.selectList(
            new LambdaQueryWrapper<SourceTable>()
                .eq(SourceTable::getScope, 1)
                .or()
                .eq(SourceTable::getScope, 2).eq(SourceTable::getOwnerId, currentUserId)
                .orderByDesc(SourceTable::getCreatedAt));
    }

    @Override
    public IPage<SourceTable> findPageable(String search, Integer scope, Long currentUserId, int page, int size) {
        LambdaQueryWrapper<SourceTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(SourceTable::getScope, 1)
            .or().eq(SourceTable::getScope, 2).eq(SourceTable::getOwnerId, currentUserId));
        if (scope != null) {
            if (scope == 1) {
                wrapper.eq(SourceTable::getScope, 1);
            } else if (scope == 2) {
                wrapper.eq(SourceTable::getScope, 2).eq(SourceTable::getOwnerId, currentUserId);
            }
        }
        if (StringUtils.hasText(search)) {
            wrapper.and(w -> w.like(SourceTable::getName, search)
                .or().like(SourceTable::getDescription, search));
        }
        wrapper.orderByDesc(SourceTable::getCreatedAt);
        return sourceTableMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public SourceTable save(SourceTable table) {
        if (table.getId() == null) {
            sourceTableMapper.insert(table);
        } else {
            sourceTableMapper.updateById(table);
        }
        return table;
    }

    @Override
    public void deleteById(Long id) {
        deleteColumnsByTableId(id);
        sourceTableMapper.deleteById(id);
    }

    @Override
    public Optional<TableColumn> findColumnById(Long id) {
        return Optional.ofNullable(tableColumnMapper.selectById(id));
    }

    @Override
    public TableColumn saveColumn(TableColumn column) {
        tableColumnMapper.insert(column);
        return column;
    }

    @Override
    public void updateColumn(TableColumn column) {
        tableColumnMapper.updateById(column);
    }

    @Override
    public void deleteColumnById(Long id) {
        tableColumnMapper.deleteById(id);
    }

    @Override
    public List<TableColumn> findColumnsByTableId(Long tableId) {
        return tableColumnMapper.selectList(
            new LambdaQueryWrapper<TableColumn>()
                .eq(TableColumn::getTableId, tableId)
                .orderByAsc(TableColumn::getSortOrder));
    }

    @Override
    public void saveColumns(List<TableColumn> columns) {
        columns.forEach(tableColumnMapper::insert);
    }

    @Override
    public void deleteColumnsByTableId(Long tableId) {
        tableColumnMapper.delete(
            new LambdaQueryWrapper<TableColumn>().eq(TableColumn::getTableId, tableId));
    }

    @Override
    public List<TableWithColumns> findWithColumnsByProjectId(Long projectId) {
        return sourceTableMapper.selectWithColumnsByProjectId(projectId);
    }

    @Override
    public List<TableWithColumns> findWithColumnsByConversationId(Long conversationId) {
        return sourceTableMapper.selectWithColumnsByConversationId(conversationId);
    }
}
