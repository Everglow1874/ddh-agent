package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

@Data
public class GraphColumnResponse {
    private Long id;
    private String name;
    private String type;
    private String comment;
}
