package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TableResponse {
    private Long id;
    private String name;
    private String description;
    private Integer scope;
    private Long ownerId;
    private LocalDateTime createdAt;
}
