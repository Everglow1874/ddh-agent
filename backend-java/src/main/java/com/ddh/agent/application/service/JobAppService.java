package com.ddh.agent.application.service;

import com.ddh.agent.application.dto.JobResponse;
import com.ddh.agent.application.dto.StepResponse;
import com.ddh.agent.domain.model.etl.EtlJob;
import com.ddh.agent.domain.model.etl.EtlRepository;
import com.ddh.agent.domain.model.etl.EtlStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.*;

@Service
public class JobAppService {

    @Autowired private EtlRepository etlRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<JobResponse> listJobs(Long projectId) {
        return etlRepository.findJobsByProjectId(projectId).stream()
            .map(job -> JobResponse.from(job, parseSchema(job), stepsOf(job.getId())))
            .collect(Collectors.toList());
    }

    public JobResponse getJob(Long jobId) {
        EtlJob job = etlRepository.findJobById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ETL job not found"));
        return JobResponse.from(job, parseSchema(job), stepsOf(jobId));
    }

    /** 返回某 step 的 SQL 内容，结构与 Python 一致：{step_id, step_name, sql} */
    public Map<String, Object> getSql(Long jobId, Long stepId) {
        etlRepository.findJobById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ETL job not found"));
        EtlStep step = etlRepository.findStepById(stepId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));
        if (!step.getJobId().equals(jobId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found");
        }
        if (step.getSqlFilePath() == null || !Files.exists(Paths.get(step.getSqlFilePath()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SQL file not found");
        }
        String sql;
        try {
            sql = new String(Files.readAllBytes(Paths.get(step.getSqlFilePath())),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SQL file not found");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("step_id", stepId);
        result.put("step_name", step.getStepName());
        result.put("sql", sql);
        return result;
    }

    /** 将 job 的全部 .sql + plan.md 打包成 zip 字节数组。 */
    public byte[] downloadZip(Long jobId) {
        etlRepository.findJobById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ETL job not found"));
        EtlJob job = etlRepository.findJobById(jobId).get();
        List<EtlStep> steps = etlRepository.findStepsByJobId(jobId);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (EtlStep step : steps) {
                if (step.getSqlFilePath() != null) {
                    Path p = Paths.get(step.getSqlFilePath());
                    if (Files.exists(p)) {
                        addToZip(zos, p, p.getFileName().toString());
                    }
                }
            }
            if (job.getPlanMdPath() != null) {
                Path planPath = Paths.get(job.getPlanMdPath());
                if (Files.exists(planPath)) {
                    addToZip(zos, planPath, "plan.md");
                }
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build zip");
        }
        return bos.toByteArray();
    }

    private void addToZip(ZipOutputStream zos, Path path, String entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        Files.copy(path, zos);
        zos.closeEntry();
    }

    private Object parseSchema(EtlJob job) {
        if (job.getTargetSchema() == null || job.getTargetSchema().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(job.getTargetSchema(), Object.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<StepResponse> stepsOf(Long jobId) {
        return etlRepository.findStepsByJobId(jobId).stream()
            .sorted(Comparator.comparing(EtlStep::getStepOrder))
            .map(StepResponse::from)
            .collect(Collectors.toList());
    }
}
