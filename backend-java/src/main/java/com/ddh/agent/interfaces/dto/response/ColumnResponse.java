package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

@Data
public class ColumnResponse {
    private Long id;
    private String columnName;
    private String dataType;
    private String comment;
    private Integer sortOrder;
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
