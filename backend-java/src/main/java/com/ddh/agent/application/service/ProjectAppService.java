package com.ddh.agent.application.service;

import com.ddh.agent.application.assembler.ProjectAssembler;
import com.ddh.agent.application.assembler.TableAssembler;
import com.ddh.agent.domain.model.project.*;
import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.interfaces.dto.request.*;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProjectAppService {

    @Autowired private ProjectRepository projectRepository;
    @Autowired private SourceTableRepository sourceTableRepository;
    @Autowired private ProjectAssembler projectAssembler;
    @Autowired private TableAssembler tableAssembler;

    public List<ProjectResponse> listProjects(Long ownerId) {
        return projectRepository.findByOwnerId(ownerId).stream()
            .map(projectAssembler::toResponse).collect(Collectors.toList());
    }

    public ProjectResponse getProject(Long id, Long ownerId) {
        return projectAssembler.toResponse(getOwnedProject(id, ownerId));
    }

    public ProjectResponse createProject(ProjectCreateRequest req, Long ownerId) {
        Project p = new Project();
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setOwnerId(ownerId);
        p.setStatus(1);
        p.setCreatedAt(LocalDateTime.now());
        projectRepository.save(p);
        return projectAssembler.toResponse(p);
    }

    public ProjectResponse updateProject(Long id, ProjectUpdateRequest req, Long ownerId) {
        Project p = getOwnedProject(id, ownerId);
        if (req.getName() != null) p.setName(req.getName());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getStatus() != null) p.setStatus(req.getStatus());
        projectRepository.save(p);
        return projectAssembler.toResponse(p);
    }

    public void deleteProject(Long id, Long ownerId) {
        getOwnedProject(id, ownerId);
        projectRepository.deleteById(id);
    }

    public List<TableResponse> getProjectTables(Long projectId, Long ownerId) {
        getOwnedProject(projectId, ownerId);
        List<ProjectTable> rows = projectRepository.findTablesByProjectId(projectId);
        return rows.stream()
            .map(pt -> sourceTableRepository.findById(pt.getTableId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(tableAssembler::toResponse)
            .collect(Collectors.toList());
    }

    /** 关联数据表，返回新增数量。对应 Python AssociateResult(associated=...) */
    public Map<String, Object> associateTables(Long projectId,
                                               TableAssociateRequest req,
                                               Long ownerId) {
        getOwnedProject(projectId, ownerId);
        int added = 0;
        List<Long> tableIds = req.getTableIds() != null ? req.getTableIds() : Collections.emptyList();
        for (Long tableId : tableIds) {
            if (!projectRepository.existsProjectTable(projectId, tableId)) {
                projectRepository.addProjectTable(projectId, tableId);
                added++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("associated", added);
        return result;
    }

    public void removeTable(Long projectId, Long tableId, Long ownerId) {
        getOwnedProject(projectId, ownerId);
        if (!projectRepository.existsProjectTable(projectId, tableId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Association not found");
        }
        projectRepository.removeProjectTable(projectId, tableId);
    }

    public List<TableDetailResponse> getProjectTablesWithDetails(Long projectId, Long ownerId) {
        getOwnedProject(projectId, ownerId);
        return sourceTableRepository.findWithColumnsByProjectId(projectId).stream()
            .map(tableAssembler::toDetailResponse)
            .collect(Collectors.toList());
    }

    private Project getOwnedProject(Long id, Long ownerId) {
        Project p = projectRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Project not found"));
        if (!p.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your project");
        }
        return p;
    }
}