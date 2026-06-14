package com.ddh.agent.application.dto;

import com.ddh.agent.domain.model.etl.EtlJob;
import java.time.LocalDateTime;
import java.util.List;

public class JobResponse {
    public Long id;
    public Long projectId;
    public String targetTable;
    /** 反序列化后的 JSON 数组，对应前端 target_schema: unknown[] | null */
    public Object targetSchema;
    public String planMdPath;
    public LocalDateTime createdAt;
    public List<StepResponse> steps;

    public static JobResponse from(EtlJob job, Object targetSchema, List<StepResponse> steps) {
        JobResponse r = new JobResponse();
        r.id = job.getId();
        r.projectId = job.getProjectId();
        r.targetTable = job.getTargetTable();
        r.targetSchema = targetSchema;
        r.planMdPath = job.getPlanMdPath();
        r.createdAt = job.getCreatedAt();
        r.steps = steps;
        return r;
    }
}
