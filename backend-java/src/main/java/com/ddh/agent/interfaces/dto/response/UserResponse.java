package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

@Data
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private Integer role;
}
