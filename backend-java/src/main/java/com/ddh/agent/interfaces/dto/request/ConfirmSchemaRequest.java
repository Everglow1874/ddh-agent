package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ConfirmSchemaRequest {
    private String targetTable;
    private List<Map<String, Object>> columns;
}
