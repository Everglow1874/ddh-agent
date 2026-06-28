package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class GraphEdgeResponse {
    private String source;
    private String target;
    private String relationType;
    private List<ColumnPairResponse> columnPairs;
    private Long relationId;
}
