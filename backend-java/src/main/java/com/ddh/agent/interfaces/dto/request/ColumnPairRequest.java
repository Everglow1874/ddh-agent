package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

@Data
public class ColumnPairRequest {
    private Long sourceColumnId;
    private Long targetColumnId;
}
