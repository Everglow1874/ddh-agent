package com.ddh.agent.domain.model.project;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
    Optional<Project> findById(Long id);
    List<Project> findByOwnerId(Long ownerId);
    Project save(Project project);
    void deleteById(Long id);

    List<ProjectTable> findTablesByProjectId(Long projectId);
    boolean existsProjectTable(Long projectId, Long tableId);
    void addProjectTable(Long projectId, Long tableId);
    void removeProjectTable(Long projectId, Long tableId);
}
