package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

@Data
public class ColumnCreateRequest {
    private String columnName;
    private String dataType;
    private String comment;
    private Integer colLength;
    private Integer colPrecision;
    private Integer isDistributionKey;
    private Integer isPartitionKey;
    private Integer isPrimaryKey;
    private Integer isNullable;
    private String codeInfo;
    private String defaultValue;
    private Integer downstreamJobCount;
}
