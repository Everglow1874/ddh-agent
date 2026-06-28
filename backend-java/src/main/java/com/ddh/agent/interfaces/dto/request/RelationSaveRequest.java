package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class RelationSaveRequest {
    private Long sourceTableId;
    private Long targetTableId;
    private String relationType;
    private String description;
    private List<ColumnPairRequest> columnPairs;
}
