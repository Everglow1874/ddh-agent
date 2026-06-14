package com.ddh.agent.domain.service;

import com.ddh.agent.domain.model.etl.*;
import com.ddh.agent.infrastructure.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class EtlDomainService {

    @Autowired private EtlRepository etlRepository;
    @Autowired private AppProperties appProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern UNSAFE = Pattern.compile("[^a-zA-Z0-9_\\u4e00-\\u9fff]");

    public String writeSqlFile(Long projectId, int stepOrder,
                               String stepName, String sql) {
        Path dir = projectDir(projectId);
        String filename = "step" + stepOrder + "_" + safeName(stepName) + ".sql";
        Path path = dir.resolve(filename);
        try {
            Files.write(path, sql.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write SQL file: " + path, e);
        }
        return path.toString();
    }

    public String writePlanMd(Long projectId, String targetTable,
                              String requirement, List<Map<String, Object>> steps) {
        Path dir = projectDir(projectId);
        StringBuilder sb = new StringBuilder();
        sb.append("# ETL 执行计划\n\n## 需求描述\n\n").append(requirement)
          .append("\n\n## 目标表\n\n`").append(targetTable).append("`\n\n## ETL 步骤\n\n");
        for (Map<String, Object> step : steps) {
            boolean isTemp = Boolean.TRUE.equals(step.get("is_temp_table"));
            sb.append("### Step ").append(step.get("step_order"))
              .append(": ").append(step.get("step_name"))
              .append(isTemp ? "（临时表）" : "").append("\n\n")
              .append(String.valueOf(step.getOrDefault("description", ""))).append("\n\n")
              .append("输出表：`").append(String.valueOf(step.getOrDefault("output_table", ""))).append("`\n\n");
        }
        Path path = dir.resolve("plan.md");
        try {
            Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write plan.md", e);
        }
        return path.toString();
    }

    public EtlJob createJob(Long projectId, String targetTable,
                            List<Map<String, Object>> targetSchema, String planMdPath) {
        EtlJob job = new EtlJob();
        job.setProjectId(projectId);
        job.setTargetTable(targetTable);
        try { job.setTargetSchema(mapper.writeValueAsString(targetSchema)); }
        catch (Exception e) { job.setTargetSchema("[]"); }
        job.setPlanMdPath(planMdPath);
        job.setCreatedAt(LocalDateTime.now());
        return etlRepository.saveJob(job);
    }

    public EtlStep createStep(Long jobId, int stepOrder, String stepName,
                              boolean isTempTable, String sqlFilePath) {
        EtlStep step = new EtlStep();
        step.setJobId(jobId);
        step.setStepOrder(stepOrder);
        step.setStepName(stepName);
        step.setIsTempTable(isTempTable ? 1 : 0);
        step.setSqlFilePath(sqlFilePath);
        return etlRepository.saveStep(step);
    }

    private Path projectDir(Long projectId) {
        Path dir = Paths.get(appProperties.getProjectsDir()).resolve(projectId.toString());
        try { Files.createDirectories(dir); }
        catch (IOException e) { throw new RuntimeException("Cannot create project dir", e); }
        return dir;
    }

    private String safeName(String s) {
        if (s == null) s = "";
        String cleaned = UNSAFE.matcher(s).replaceAll("_");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
