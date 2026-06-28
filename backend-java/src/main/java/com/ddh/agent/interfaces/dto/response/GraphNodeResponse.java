package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class GraphNodeResponse {
    private String id;
    private Long tableId;
    private String tableName;
    private String tableComment;
    private List<GraphColumnResponse> columns;
}
