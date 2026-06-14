package com.ddh.agent.application.assembler;

import com.ddh.agent.domain.model.project.Project;
import com.ddh.agent.interfaces.dto.response.ProjectResponse;
import org.springframework.stereotype.Component;

@Component
public class ProjectAssembler {
    public ProjectResponse toResponse(Project p) {
        ProjectResponse r = new ProjectResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setDescription(p.getDescription());
        r.setOwnerId(p.getOwnerId());
        r.setStatus(p.getStatus());
        r.setCreatedAt(p.getCreatedAt());
        return r;
    }
}