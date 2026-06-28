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

    List<TableRelationColumn> findColumnsByRelationIds(List<Long> relationIds);

    void saveColumn(TableRelationColumn column);

    void deleteColumnsByRelationId(Long relationId);
}
