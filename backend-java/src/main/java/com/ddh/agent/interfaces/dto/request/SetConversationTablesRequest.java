package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class SetConversationTablesRequest {
    private List<Long> tableIds;
}
