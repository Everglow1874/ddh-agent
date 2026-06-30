package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

@Data
public class DialectFunctionRuleRequest {
    private String functionName;
    private String signature;
    private String description;
    private String example;
    private String note;
    private Integer enabled;
    private Integer sortOrder;
}
