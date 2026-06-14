package com.ddh.agent.domain.model.etl;

import java.util.List;
import java.util.Optional;

public interface EtlRepository {
    Optional<EtlJob> findJobById(Long id);
    List<EtlJob> findJobsByProjectId(Long projectId);
    EtlJob saveJob(EtlJob job);

    List<EtlStep> findStepsByJobId(Long jobId);
    Optional<EtlStep> findStepById(Long id);
    EtlStep saveStep(EtlStep step);
}
