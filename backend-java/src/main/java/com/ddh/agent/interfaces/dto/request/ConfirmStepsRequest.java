package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ConfirmStepsRequest {
    private List<Map<String, Object>> steps;
}
