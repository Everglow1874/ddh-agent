package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

@Data
public class DialectTypeRuleRequest {
    private String typeName;
    private String allowedForms;
    private String roundingRule;
    private String platformSyntax;
    private String note;
    private Integer enabled;
    private Integer sortOrder;
}
