package com.ddh.agent.interfaces.dto.request;

import lombok.Data;

@Data
public class ProjectUpdateRequest {
    private String name;
    private String description;
    private Integer status;
}
