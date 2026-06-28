package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

@Data
public class ColumnUpdateRequest {
    private String columnName;
    private String dataType;
    private String comment;
    private Integer sortOrder;
}
