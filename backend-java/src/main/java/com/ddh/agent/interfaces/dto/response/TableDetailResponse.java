package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class TableDetailResponse {
    private Long id;
    private String name;
    private String description;
    private Integer scope;
    private Long ownerId;
    private List<ColumnResponse> columns;
}
