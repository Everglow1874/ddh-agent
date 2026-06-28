package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class RelationResponse {
    private Long id;
    private Long sourceTableId;
    private String sourceTableName;
    private String sourceTableComment;
    private Long targetTableId;
    private String targetTableName;
    private String targetTableComment;
    private String relationType;
    private String description;
    private List<ColumnPairResponse> columnPairs;
}
