package com.ddh.agent.application.dto;

/** LLM 配置出入参，结构与 Python /admin/config 保持一致：{provider, model}。 */
public class AdminConfigDto {
    public String provider;
    public String model;

    public AdminConfigDto() {}

    public AdminConfigDto(String provider, String model) {
        this.provider = provider;
        this.model = model;
    }
}
