package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

@Data
public class ColumnCreateRequest {
    private String columnName;
    private String dataType;
    private String comment;
}
