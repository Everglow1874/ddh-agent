package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class TableAssociateRequest {
    private List<Long> tableIds;
}
