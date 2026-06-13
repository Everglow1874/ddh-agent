package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConversationResponse {
    private Long id;
    private Long projectId;
    private Integer state;
    private LocalDateTime createdAt;
    private List<Long> tableIds;
}
