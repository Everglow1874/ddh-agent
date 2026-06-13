package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.agent.domain.model.etl.*;
import com.ddh.agent.infrastructure.persistence.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class EtlRepositoryImpl implements EtlRepository {

    @Autowired private EtlJobMapper etlJobMapper;
    @Autowired private EtlStepMapper etlStepMapper;

    @Override
    public Optional<EtlJob> findJobById(Long id) {
        return Optional.ofNullable(etlJobMapper.selectById(id));
    }

    @Override
    public List<EtlJob> findJobsByProjectId(Long projectId) {
        return etlJobMapper.selectList(
            new LambdaQueryWrapper<EtlJob>()
                .eq(EtlJob::getProjectId, projectId)
                .orderByDesc(EtlJob::getCreatedAt));
    }

    @Override
    public EtlJob saveJob(EtlJob job) {
        if (job.getId() == null) {
            etlJobMapper.insert(job);
        } else {
            etlJobMapper.updateById(job);
        }
        return job;
    }

    @Override
    public List<EtlStep> findStepsByJobId(Long jobId) {
        return etlStepMapper.selectList(
            new LambdaQueryWrapper<EtlStep>()
                .eq(EtlStep::getJobId, jobId)
                .orderByAsc(EtlStep::getStepOrder));
    }

    @Override
    public Optional<EtlStep> findStepById(Long id) {
        return Optional.ofNullable(etlStepMapper.selectById(id));
    }

    @Override
    public EtlStep saveStep(EtlStep step) {
        etlStepMapper.insert(step);
        return step;
    }
}
