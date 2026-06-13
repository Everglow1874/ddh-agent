package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

@Data
public class ColumnResponse {
    private Long id;
    private String columnName;
    private String dataType;
    private String comment;
    private Integer sortOrder;
}
