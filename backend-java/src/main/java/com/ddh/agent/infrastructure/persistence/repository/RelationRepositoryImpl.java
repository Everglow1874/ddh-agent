package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ddh.agent.domain.model.relation.RelationRepository;
import com.ddh.agent.domain.model.relation.TableRelation;
import com.ddh.agent.domain.model.relation.TableRelationColumn;
import com.ddh.agent.infrastructure.persistence.mapper.TableRelationColumnMapper;
import com.ddh.agent.infrastructure.persistence.mapper.TableRelationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class RelationRepositoryImpl implements RelationRepository {

    @Autowired private TableRelationMapper relationMapper;
    @Autowired private TableRelationColumnMapper relationColumnMapper;

    @Override
    public List<TableRelation> findAll() {
        return relationMapper.selectList(
            new LambdaQueryWrapper<TableRelation>()
                .orderByDesc(TableRelation::getCreatedAt));
    }

    @Override
    public Optional<TableRelation> findById(Long id) {
        return Optional.ofNullable(relationMapper.selectById(id));
    }

    @Override
    public TableRelation save(TableRelation relation) {
        if (relation.getId() == null) {
            relationMapper.insert(relation);
        } else {
            relationMapper.updateById(relation);
        }
        return relation;
    }

    @Override
    public void deleteById(Long id) {
        relationMapper.deleteById(id);
    }

    @Override
    public List<TableRelation> findTouchingTables(List<Long> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) {
            return Collections.emptyList();
        }
        return relationMapper.selectList(
            new LambdaQueryWrapper<TableRelation>()
                .in(TableRelation::getSourceTableId, tableIds)
                .or()
                .in(TableRelation::getTargetTableId, tableIds));
    }

    @Override
    public List<TableRelationColumn> findColumnsByRelationIds(List<Long> relationIds) {
        if (relationIds == null || relationIds.isEmpty()) {
            return Collections.emptyList();
        }
        return relationColumnMapper.selectList(
            new LambdaQueryWrapper<TableRelationColumn>()
                .in(TableRelationColumn::getRelationId, relationIds)
                .orderByAsc(TableRelationColumn::getSortOrder));
    }

    @Override
    public void saveColumn(TableRelationColumn column) {
        relationColumnMapper.insert(column);
    }

    @Override
    public void deleteColumnsByRelationId(Long relationId) {
        relationColumnMapper.delete(
            new LambdaQueryWrapper<TableRelationColumn>()
                .eq(TableRelationColumn::getRelationId, relationId));
    }

    @Override
    public IPage<TableRelation> findPage(int page, int size, String search) {
        LambdaQueryWrapper<TableRelation> wrapper = new LambdaQueryWrapper<TableRelation>()
                .orderByDesc(TableRelation::getCreatedAt);
        if (StringUtils.hasText(search)) {
            wrapper.like(TableRelation::getDescription, search);
        }
        return relationMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
