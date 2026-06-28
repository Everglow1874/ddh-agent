package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

@Data
public class ColumnPairResponse {
    private Long sourceColumnId;
    private String sourceColumnName;
    private Long targetColumnId;
    private String targetColumnName;
}
