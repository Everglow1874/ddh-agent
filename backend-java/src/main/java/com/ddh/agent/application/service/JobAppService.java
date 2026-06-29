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
        String sql = step.getSqlContent();
        if (sql == null) {
            if (step.getSqlFilePath() == null || !Files.exists(Paths.get(step.getSqlFilePath()))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SQL file not found");
            }
            try {
                sql = new String(Files.readAllBytes(Paths.get(step.getSqlFilePath())),
                    StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SQL file not found");
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("step_id", stepId);
        result.put("step_name", step.getStepName());
        result.put("sql", sql);
        return result;
    }

    /**
     * 将 job 的全部 .sql + plan.md 打包成 zip。
     * 每次从数据库内容重新生成文件到临时目录，打包后清理临时文件。
     */
    public byte[] downloadZip(Long jobId) {
        EtlJob job = etlRepository.findJobById(jobId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ETL job not found"));
        List<EtlStep> steps = etlRepository.findStepsByJobId(jobId);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (EtlStep step : steps) {
                String content = step.getSqlContent();
                if (content == null && step.getSqlFilePath() != null) {
                    content = readFileSafe(Paths.get(step.getSqlFilePath()));
                }
                if (content != null && !content.isEmpty()) {
                    String filename = "step" + step.getStepOrder() + "_" + safeEntryName(step.getStepName()) + ".sql";
                    zos.putNextEntry(new ZipEntry(filename));
                    zos.write(content.getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }
            String planContent = job.getPlanContent();
            if (planContent == null && job.getPlanMdPath() != null) {
                planContent = readFileSafe(Paths.get(job.getPlanMdPath()));
            }
            if (planContent != null && !planContent.isEmpty()) {
                zos.putNextEntry(new ZipEntry("plan.md"));
                zos.write(planContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build zip");
        }
        return bos.toByteArray();
    }

    private static String readFileSafe(Path path) {
        try {
            return Files.exists(path)
                ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** 删除 job 对应的文件系统源文件（.sql + plan.md）。仅当 DB 中有 content 时才删，保护旧数据。
     *  文件删完后，若所在的 projects/{projectId} 目录已空，连空目录一并清理。 */
    public void cleanupSourceFiles(Long jobId) {
        EtlJob job = etlRepository.findJobById(jobId).orElse(null);
        if (job == null) return;
        Set<Path> touchedDirs = new HashSet<>();
        if (job.getPlanMdPath() != null && job.getPlanContent() != null) {
            deleteFileTrackDir(job.getPlanMdPath(), touchedDirs);
        }
        for (EtlStep step : etlRepository.findStepsByJobId(jobId)) {
            if (step.getSqlFilePath() != null && step.getSqlContent() != null) {
                deleteFileTrackDir(step.getSqlFilePath(), touchedDirs);
            }
        }
        // 删除已空的目录（其它 job 仍占用同目录时 delete 会抛 DirectoryNotEmptyException，被忽略）
        for (Path dir : touchedDirs) {
            try { Files.deleteIfExists(dir); } catch (IOException ignored) {}
        }
    }

    private void deleteFileTrackDir(String filePath, Set<Path> dirs) {
        Path p = Paths.get(filePath);
        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
        Path parent = p.getParent();
        if (parent != null) dirs.add(parent);
    }

    private static String safeEntryName(String name) {
        if (name == null) return "";
        return name.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fff]", "_")
                   .replaceAll("_+", "_")
                   .replaceAll("^_|_$", "");
    }

    /**
     * 返回某对话最近一次生成的作业，用于页面刷新后恢复右侧 SQL 面板。
     * 结构 {job_id, steps:[{step_order, step_name, sql}]}；无作业时 job_id=null、steps=[]。
     */
    public Map<String, Object> getConversationJob(Long conversationId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Optional<EtlJob> jobOpt = etlRepository.findLatestJobByConversationId(conversationId);
        if (!jobOpt.isPresent()) {
            result.put("job_id", null);
            result.put("steps", Collections.emptyList());
            return result;
        }
        EtlJob job = jobOpt.get();
        List<Map<String, Object>> steps = new ArrayList<>();
        for (EtlStep step : etlRepository.findStepsByJobId(job.getId())) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("step_order", step.getStepOrder());
            sm.put("step_name", step.getStepName());
            sm.put("sql", readSqlSafe(step));
            steps.add(sm);
        }
        result.put("job_id", job.getId());
        result.put("steps", steps);
        return result;
    }

    private String readSqlSafe(EtlStep step) {
        if (step.getSqlContent() != null) return step.getSqlContent();
        if (step.getSqlFilePath() == null) return "";
        try {
            Path p = Paths.get(step.getSqlFilePath());
            return Files.exists(p)
                ? new String(Files.readAllBytes(p), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
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
