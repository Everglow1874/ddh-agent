package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.agent.domain.model.project.*;
import com.ddh.agent.infrastructure.persistence.mapper.ProjectMapper;
import com.ddh.agent.infrastructure.persistence.mapper.ProjectTableMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class ProjectRepositoryImpl implements ProjectRepository {

    @Autowired private ProjectMapper projectMapper;
    @Autowired private ProjectTableMapper projectTableMapper;

    @Override
    public Optional<Project> findById(Long id) {
        return Optional.ofNullable(projectMapper.selectById(id));
    }

    @Override
    public List<Project> findByOwnerId(Long ownerId) {
        return projectMapper.selectList(
            new LambdaQueryWrapper<Project>().eq(Project::getOwnerId, ownerId)
                .orderByDesc(Project::getCreatedAt));
    }

    @Override
    public Project save(Project project) {
        if (project.getId() == null) {
            projectMapper.insert(project);
        } else {
            projectMapper.updateById(project);
        }
        return project;
    }

    @Override
    public void deleteById(Long id) {
        projectMapper.deleteById(id);
    }

    @Override
    public List<ProjectTable> findTablesByProjectId(Long projectId) {
        return projectTableMapper.selectList(
            new LambdaQueryWrapper<ProjectTable>().eq(ProjectTable::getProjectId, projectId));
    }

    @Override
    public boolean existsProjectTable(Long projectId, Long tableId) {
        return projectTableMapper.selectCount(
            new LambdaQueryWrapper<ProjectTable>()
                .eq(ProjectTable::getProjectId, projectId)
                .eq(ProjectTable::getTableId, tableId)) > 0;
    }

    @Override
    public void addProjectTable(Long projectId, Long tableId) {
        ProjectTable pt = new ProjectTable();
        pt.setProjectId(projectId);
        pt.setTableId(tableId);
        projectTableMapper.insert(pt);
    }

    @Override
    public void removeProjectTable(Long projectId, Long tableId) {
        projectTableMapper.delete(
            new LambdaQueryWrapper<ProjectTable>()
                .eq(ProjectTable::getProjectId, projectId)
                .eq(ProjectTable::getTableId, tableId));
    }
}
