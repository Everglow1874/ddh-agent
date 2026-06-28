package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class CreateConversationRequest {
    private String name;
    private List<Long> tableIds = Collections.emptyList();
}
