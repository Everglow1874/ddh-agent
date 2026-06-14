package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.service.ProjectAppService;
import com.ddh.agent.interfaces.dto.request.*;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired private ProjectAppService projectAppService;

    @GetMapping
    public List<ProjectResponse> list(Authentication auth) {
        return projectAppService.listProjects(Long.valueOf(auth.getName()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@RequestBody ProjectCreateRequest req, Authentication auth) {
        return projectAppService.createProject(req, Long.valueOf(auth.getName()));
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id, Authentication auth) {
        return projectAppService.getProject(id, Long.valueOf(auth.getName()));
    }

    @PutMapping("/{id}")
    public ProjectResponse update(@PathVariable Long id,
                                  @RequestBody ProjectUpdateRequest req,
                                  Authentication auth) {
        return projectAppService.updateProject(id, req, Long.valueOf(auth.getName()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        projectAppService.deleteProject(id, Long.valueOf(auth.getName()));
    }

    @GetMapping("/{id}/tables")
    public List<TableResponse> getTables(@PathVariable Long id, Authentication auth) {
        return projectAppService.getProjectTables(id, Long.valueOf(auth.getName()));
    }

    @PostMapping("/{id}/tables")
    public Map<String, Object> associateTables(@PathVariable Long id,
                                               @RequestBody TableAssociateRequest req,
                                               Authentication auth) {
        return projectAppService.associateTables(id, req, Long.valueOf(auth.getName()));
    }

    @DeleteMapping("/{id}/tables/{tableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTable(@PathVariable Long id,
                            @PathVariable Long tableId,
                            Authentication auth) {
        projectAppService.removeTable(id, tableId, Long.valueOf(auth.getName()));
    }

    @GetMapping("/{id}/tables-with-details")
    public List<TableDetailResponse> tablesWithDetails(@PathVariable Long id,
                                                       Authentication auth) {
        return projectAppService.getProjectTablesWithDetails(id, Long.valueOf(auth.getName()));
    }
}