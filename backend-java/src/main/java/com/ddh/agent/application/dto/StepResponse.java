package com.ddh.agent.application.dto;

import com.ddh.agent.domain.model.etl.EtlStep;

public class StepResponse {
    public Long id;
    public Long jobId;
    public Integer stepOrder;
    public String stepName;
    public Integer isTempTable;
    public String sqlFilePath;

    public static StepResponse from(EtlStep step) {
        StepResponse r = new StepResponse();
        r.id = step.getId();
        r.jobId = step.getJobId();
        r.stepOrder = step.getStepOrder();
        r.stepName = step.getStepName();
        r.isTempTable = step.getIsTempTable();
        r.sqlFilePath = step.getSqlFilePath();
        return r;
    }
}
