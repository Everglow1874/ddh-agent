package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.dto.JobResponse;
import com.ddh.agent.application.service.JobAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class JobController {

    @Autowired private JobAppService jobAppService;

    @GetMapping("/projects/{projectId}/jobs")
    public List<JobResponse> listJobs(@PathVariable Long projectId, Authentication auth) {
        return jobAppService.listJobs(projectId);
    }

    @GetMapping("/jobs/{jobId}")
    public JobResponse getJob(@PathVariable Long jobId, Authentication auth) {
        return jobAppService.getJob(jobId);
    }

    @GetMapping("/jobs/{jobId}/steps/{stepId}/sql")
    public Map<String, Object> getSql(@PathVariable Long jobId, @PathVariable Long stepId,
                                      Authentication auth) {
        return jobAppService.getSql(jobId, stepId);
    }

    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long jobId, Authentication auth) {
        byte[] zip = jobAppService.downloadZip(jobId);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=etl_job_" + jobId + ".zip")
            .contentType(MediaType.parseMediaType("application/zip"))
            .body(zip);
    }
}
