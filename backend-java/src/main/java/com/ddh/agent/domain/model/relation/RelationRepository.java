package com.ddh.agent.domain.model.relation;

import java.util.List;
import java.util.Optional;

public interface RelationRepository {
    /** 全部关系（按创建时间倒序）。 */
    List<TableRelation> findAll();

    Optional<TableRelation> findById(Long id);

    TableRelation save(TableRelation relation);

    void deleteById(Long id);

    /** 任一端命中给定表集合的关系。 */
    List<TableRelation> findTouchingTables(List<Long> tableIds);

    /** 分页查询，search 匹配 description。 */
    com.baomidou.mybatisplus.core.metadata.IPage<TableRelation> findPage(int page, int size, String search);

    List<TableRelationColumn> findColumnsByRelationIds(List<Long> relationIds);

    void saveColumn(TableRelationColumn column);

    void deleteColumnsByRelationId(Long relationId);
}
