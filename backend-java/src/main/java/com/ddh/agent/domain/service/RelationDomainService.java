package com.ddh.agent.domain.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ddh.agent.domain.model.relation.RelationRepository;
import com.ddh.agent.domain.model.relation.TableRelation;
import com.ddh.agent.domain.model.relation.TableRelationColumn;
import com.ddh.agent.domain.model.table.SourceTable;
import com.ddh.agent.domain.model.table.SourceTableRepository;
import com.ddh.agent.domain.model.table.TableColumn;
import com.ddh.agent.interfaces.dto.request.ColumnPairRequest;
import com.ddh.agent.interfaces.dto.request.RelationSaveRequest;
import com.ddh.agent.interfaces.dto.response.ColumnPairResponse;
import com.ddh.agent.interfaces.dto.response.GraphColumnResponse;
import com.ddh.agent.interfaces.dto.response.GraphEdgeResponse;
import com.ddh.agent.interfaces.dto.response.GraphNodeResponse;
import com.ddh.agent.interfaces.dto.response.LineageGraphResponse;
import com.ddh.agent.interfaces.dto.response.PageResponse;
import com.ddh.agent.interfaces.dto.response.RelationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 表关系领域服务（全局维度，关系挂在 source_tables 之间）。
 * 迁移自 ddh-assistant 的 RelationService，去掉 projectId，改为按「表对当前用户可见」校验。
 */
@Service
public class RelationDomainService {

    @Autowired private RelationRepository relationRepository;
    @Autowired private SourceTableRepository sourceTableRepository;

    public static String relationTypeLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case "ONE_TO_ONE": return "一对一";
            case "ONE_TO_MANY": return "一对多";
            case "MANY_TO_ONE": return "多对一";
            case "MANY_TO_MANY": return "多对多";
            default: return type;
        }
    }

    // ========== 查询 ==========

    @Transactional(readOnly = true)
    public List<RelationResponse> listRelations(Long currentUserId) {
        List<TableRelation> relations = relationRepository.findAll();
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, SourceTable> tableMap = loadTables(relations);
        // 仅保留两端表都对当前用户可见的关系
        List<TableRelation> visible = relations.stream()
                .filter(r -> isVisible(tableMap.get(r.getSourceTableId()), currentUserId)
                          && isVisible(tableMap.get(r.getTargetTableId()), currentUserId))
                .collect(Collectors.toList());
        if (visible.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> relationIds = visible.stream().map(TableRelation::getId).collect(Collectors.toList());
        List<TableRelationColumn> pairs = relationRepository.findColumnsByRelationIds(relationIds);
        Map<Long, List<TableRelationColumn>> pairsByRelation = pairs.stream()
                .collect(Collectors.groupingBy(TableRelationColumn::getRelationId));

        Set<Long> involvedTables = new HashSet<>();
        visible.forEach(r -> { involvedTables.add(r.getSourceTableId()); involvedTables.add(r.getTargetTableId()); });
        Map<Long, TableColumn> columnMap = loadColumnsForTables(involvedTables);

        List<RelationResponse> result = new ArrayList<>();
        for (TableRelation r : visible) {
            RelationResponse vo = new RelationResponse();
            vo.setId(r.getId());
            vo.setSourceTableId(r.getSourceTableId());
            vo.setTargetTableId(r.getTargetTableId());
            vo.setRelationType(r.getRelationType());
            vo.setDescription(r.getDescription());
            SourceTable src = tableMap.get(r.getSourceTableId());
            SourceTable tgt = tableMap.get(r.getTargetTableId());
            if (src != null) { vo.setSourceTableName(src.getName()); vo.setSourceTableComment(src.getDescription()); }
            if (tgt != null) { vo.setTargetTableName(tgt.getName()); vo.setTargetTableComment(tgt.getDescription()); }
            vo.setColumnPairs(toPairResponses(
                    pairsByRelation.getOrDefault(r.getId(), Collections.emptyList()), columnMap));
            result.add(vo);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<RelationResponse> listRelationsPage(Long currentUserId, int page, int size, String search) {
        IPage<TableRelation> p = relationRepository.findPage(page, size, search);
        if (p.getRecords().isEmpty()) {
            return PageResponse.of(p, Collections.emptyList());
        }
        Map<Long, SourceTable> tableMap = loadTables(p.getRecords());
        List<TableRelation> visible = p.getRecords().stream()
                .filter(r -> isVisible(tableMap.get(r.getSourceTableId()), currentUserId)
                          && isVisible(tableMap.get(r.getTargetTableId()), currentUserId))
                .collect(Collectors.toList());
        if (visible.isEmpty()) {
            return PageResponse.of(p, Collections.emptyList());
        }

        List<Long> relationIds = visible.stream().map(TableRelation::getId).collect(Collectors.toList());
        List<TableRelationColumn> pairs = relationRepository.findColumnsByRelationIds(relationIds);
        Map<Long, List<TableRelationColumn>> pairsByRelation = pairs.stream()
                .collect(Collectors.groupingBy(TableRelationColumn::getRelationId));

        Set<Long> involvedTables = new HashSet<>();
        visible.forEach(r -> { involvedTables.add(r.getSourceTableId()); involvedTables.add(r.getTargetTableId()); });
        Map<Long, TableColumn> columnMap = loadColumnsForTables(involvedTables);

        List<RelationResponse> result = new ArrayList<>();
        for (TableRelation r : visible) {
            RelationResponse vo = new RelationResponse();
            vo.setId(r.getId());
            vo.setSourceTableId(r.getSourceTableId());
            vo.setTargetTableId(r.getTargetTableId());
            vo.setRelationType(r.getRelationType());
            vo.setDescription(r.getDescription());
            SourceTable src = tableMap.get(r.getSourceTableId());
            SourceTable tgt = tableMap.get(r.getTargetTableId());
            if (src != null) { vo.setSourceTableName(src.getName()); vo.setSourceTableComment(src.getDescription()); }
            if (tgt != null) { vo.setTargetTableName(tgt.getName()); vo.setTargetTableComment(tgt.getDescription()); }
            vo.setColumnPairs(toPairResponses(
                    pairsByRelation.getOrDefault(r.getId(), Collections.emptyList()), columnMap));
            result.add(vo);
        }
        return PageResponse.of(p, result);
    }

    // ========== 增删改 ==========

    @Transactional(rollbackFor = Exception.class)
    public Long createRelation(Long currentUserId, RelationSaveRequest req) {
        validate(currentUserId, req);
        TableRelation r = new TableRelation();
        r.setSourceTableId(req.getSourceTableId());
        r.setTargetTableId(req.getTargetTableId());
        r.setRelationType(req.getRelationType());
        r.setDescription(req.getDescription());
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        relationRepository.save(r);
        saveColumnPairs(r.getId(), req.getColumnPairs());
        return r.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRelation(Long currentUserId, Long relationId, RelationSaveRequest req) {
        TableRelation existing = relationRepository.findById(relationId)
                .orElseThrow(() -> new IllegalArgumentException("关系不存在: " + relationId));
        validate(currentUserId, req);
        existing.setSourceTableId(req.getSourceTableId());
        existing.setTargetTableId(req.getTargetTableId());
        existing.setRelationType(req.getRelationType());
        existing.setDescription(req.getDescription());
        existing.setUpdatedAt(LocalDateTime.now());
        relationRepository.save(existing);
        // 全量替换字段对
        relationRepository.deleteColumnsByRelationId(relationId);
        saveColumnPairs(relationId, req.getColumnPairs());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(Long currentUserId, Long relationId) {
        TableRelation existing = relationRepository.findById(relationId).orElse(null);
        if (existing == null) {
            return;
        }
        relationRepository.deleteColumnsByRelationId(relationId);
        relationRepository.deleteById(relationId);
    }

    private void validate(Long currentUserId, RelationSaveRequest req) {
        if (req.getSourceTableId() == null) {
            throw new IllegalArgumentException("主表ID不能为空");
        }
        if (req.getTargetTableId() == null) {
            throw new IllegalArgumentException("关联表ID不能为空");
        }
        SourceTable src = sourceTableRepository.findById(req.getSourceTableId()).orElse(null);
        if (!isVisible(src, currentUserId)) {
            throw new IllegalArgumentException("主表不存在或无权访问: " + req.getSourceTableId());
        }
        SourceTable tgt = sourceTableRepository.findById(req.getTargetTableId()).orElse(null);
        if (!isVisible(tgt, currentUserId)) {
            throw new IllegalArgumentException("关联表不存在或无权访问: " + req.getTargetTableId());
        }

        List<ColumnPairRequest> pairs = req.getColumnPairs();
        if (pairs == null || pairs.isEmpty()) {
            return;
        }
        Set<Long> srcCols = sourceTableRepository.findColumnsByTableId(src.getId()).stream()
                .map(TableColumn::getId).collect(Collectors.toSet());
        Set<Long> tgtCols = sourceTableRepository.findColumnsByTableId(tgt.getId()).stream()
                .map(TableColumn::getId).collect(Collectors.toSet());
        for (ColumnPairRequest p : pairs) {
            if (p.getSourceColumnId() != null && !srcCols.contains(p.getSourceColumnId())) {
                throw new IllegalArgumentException("关联字段不属于主表: " + p.getSourceColumnId());
            }
            if (p.getTargetColumnId() != null && !tgtCols.contains(p.getTargetColumnId())) {
                throw new IllegalArgumentException("关联字段不属于关联表: " + p.getTargetColumnId());
            }
        }
    }

    private void saveColumnPairs(Long relationId, List<ColumnPairRequest> pairs) {
        if (pairs == null) return;
        int order = 0;
        for (ColumnPairRequest p : pairs) {
            if (p.getSourceColumnId() == null && p.getTargetColumnId() == null) {
                continue;
            }
            TableRelationColumn rc = new TableRelationColumn();
            rc.setRelationId(relationId);
            rc.setSourceColumnId(p.getSourceColumnId());
            rc.setTargetColumnId(p.getTargetColumnId());
            rc.setSortOrder(order++);
            relationRepository.saveColumn(rc);
        }
    }

    // ========== 血缘图 ==========

    @Transactional(readOnly = true)
    public LineageGraphResponse buildGraph(List<Long> tableIds) {
        LineageGraphResponse graph = new LineageGraphResponse();
        graph.setNodes(new ArrayList<>());
        graph.setEdges(new ArrayList<>());
        if (tableIds == null || tableIds.isEmpty()) {
            return graph;
        }
        Set<Long> idSet = new HashSet<>(tableIds);

        for (Long tid : idSet) {
            SourceTable t = sourceTableRepository.findById(tid).orElse(null);
            if (t == null) continue;
            GraphNodeResponse node = new GraphNodeResponse();
            node.setId("t_" + t.getId());
            node.setTableId(t.getId());
            node.setTableName(t.getName());
            node.setTableComment(t.getDescription());
            List<GraphColumnResponse> cols = new ArrayList<>();
            for (TableColumn c : sourceTableRepository.findColumnsByTableId(t.getId())) {
                GraphColumnResponse gc = new GraphColumnResponse();
                gc.setName(c.getColumnName());
                gc.setType(c.getDataType());
                gc.setComment(c.getComment());
                cols.add(gc);
            }
            node.setColumns(cols);
            graph.getNodes().add(node);
        }

        // 只保留两端都在所选表内的关系
        List<TableRelation> inScope = relationRepository.findTouchingTables(tableIds).stream()
                .filter(r -> idSet.contains(r.getSourceTableId()) && idSet.contains(r.getTargetTableId()))
                .collect(Collectors.toList());
        if (!inScope.isEmpty()) {
            List<Long> relationIds = inScope.stream().map(TableRelation::getId).collect(Collectors.toList());
            List<TableRelationColumn> pairs = relationRepository.findColumnsByRelationIds(relationIds);
            Map<Long, TableColumn> columnMap = loadColumnsForTables(idSet);
            Map<Long, List<TableRelationColumn>> pairsByRelation = pairs.stream()
                    .collect(Collectors.groupingBy(TableRelationColumn::getRelationId));
            for (TableRelation r : inScope) {
                GraphEdgeResponse edge = new GraphEdgeResponse();
                edge.setSource("t_" + r.getSourceTableId());
                edge.setTarget("t_" + r.getTargetTableId());
                edge.setRelationType(r.getRelationType());
                edge.setRelationId(r.getId());
                edge.setColumnPairs(toPairResponses(
                        pairsByRelation.getOrDefault(r.getId(), Collections.emptyList()), columnMap));
                graph.getEdges().add(edge);
            }
        }
        return graph;
    }

    // ========== Agent Prompt 用关系文本 ==========

    @Transactional(readOnly = true)
    public String buildRelationTextForTables(List<Long> tableIds) {
        if (tableIds == null || tableIds.isEmpty()) return "";
        Set<Long> idSet = new HashSet<>(tableIds);
        List<TableRelation> inScope = relationRepository.findTouchingTables(tableIds).stream()
                .filter(r -> idSet.contains(r.getSourceTableId()) && idSet.contains(r.getTargetTableId()))
                .collect(Collectors.toList());
        if (inScope.isEmpty()) return "";

        List<Long> relationIds = inScope.stream().map(TableRelation::getId).collect(Collectors.toList());
        List<TableRelationColumn> pairs = relationRepository.findColumnsByRelationIds(relationIds);
        Map<Long, SourceTable> tableMap = loadTables(inScope);
        Map<Long, TableColumn> columnMap = loadColumnsForTables(idSet);
        Map<Long, List<TableRelationColumn>> pairsByRelation = pairs.stream()
                .collect(Collectors.groupingBy(TableRelationColumn::getRelationId));

        StringBuilder sb = new StringBuilder("### 表关系\n");
        for (TableRelation r : inScope) {
            SourceTable src = tableMap.get(r.getSourceTableId());
            SourceTable tgt = tableMap.get(r.getTargetTableId());
            if (src == null || tgt == null) continue;
            sb.append("- ").append(tableLabel(src)).append(" ")
              .append(relationTypeLabel(r.getRelationType())).append(" ")
              .append(tableLabel(tgt)).append("：");
            List<String> conds = new ArrayList<>();
            for (TableRelationColumn p : pairsByRelation.getOrDefault(r.getId(), Collections.emptyList())) {
                TableColumn sc = columnMap.get(p.getSourceColumnId());
                TableColumn tc = columnMap.get(p.getTargetColumnId());
                if (sc == null || tc == null) continue;
                conds.add(src.getName() + "." + sc.getColumnName()
                        + " = " + tgt.getName() + "." + tc.getColumnName());
            }
            sb.append(String.join(" AND ", conds)).append("\n");
        }
        return sb.toString();
    }

    // ========== 私有辅助 ==========

    private boolean isVisible(SourceTable t, Long currentUserId) {
        if (t == null) return false;
        return Integer.valueOf(1).equals(t.getScope())
                || (currentUserId != null && currentUserId.equals(t.getOwnerId()));
    }

    private String tableLabel(SourceTable t) {
        return StringUtils.hasText(t.getDescription())
                ? t.getName() + "(" + t.getDescription() + ")"
                : t.getName();
    }

    private Map<Long, SourceTable> loadTables(List<TableRelation> relations) {
        Set<Long> tableIds = new HashSet<>();
        for (TableRelation r : relations) {
            tableIds.add(r.getSourceTableId());
            tableIds.add(r.getTargetTableId());
        }
        Map<Long, SourceTable> map = new HashMap<>();
        for (Long id : tableIds) {
            sourceTableRepository.findById(id).ifPresent(t -> map.put(id, t));
        }
        return map;
    }

    private Map<Long, TableColumn> loadColumnsForTables(Collection<Long> tableIds) {
        Map<Long, TableColumn> map = new HashMap<>();
        for (Long tid : new HashSet<>(tableIds)) {
            for (TableColumn c : sourceTableRepository.findColumnsByTableId(tid)) {
                map.put(c.getId(), c);
            }
        }
        return map;
    }

    private List<ColumnPairResponse> toPairResponses(List<TableRelationColumn> pairs,
                                                     Map<Long, TableColumn> columnMap) {
        List<ColumnPairResponse> result = new ArrayList<>();
        for (TableRelationColumn p : pairs) {
            ColumnPairResponse vo = new ColumnPairResponse();
            vo.setSourceColumnId(p.getSourceColumnId());
            vo.setTargetColumnId(p.getTargetColumnId());
            TableColumn sc = columnMap.get(p.getSourceColumnId());
            TableColumn tc = columnMap.get(p.getTargetColumnId());
            if (sc != null) vo.setSourceColumnName(sc.getColumnName());
            if (tc != null) vo.setTargetColumnName(tc.getColumnName());
            result.add(vo);
        }
        return result;
    }
}
