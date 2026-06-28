package com.ddh.agent.application.service;

import com.ddh.agent.domain.service.RelationDomainService;
import com.ddh.agent.interfaces.dto.request.RelationSaveRequest;
import com.ddh.agent.interfaces.dto.response.LineageGraphResponse;
import com.ddh.agent.interfaces.dto.response.RelationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RelationAppService {

    @Autowired private RelationDomainService relationDomainService;

    public List<RelationResponse> list(Long currentUserId) {
        return relationDomainService.listRelations(currentUserId);
    }

    public Long create(Long currentUserId, RelationSaveRequest req) {
        try {
            return relationDomainService.createRelation(currentUserId, req);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public void update(Long currentUserId, Long relationId, RelationSaveRequest req) {
        try {
            relationDomainService.updateRelation(currentUserId, relationId, req);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public void delete(Long currentUserId, Long relationId) {
        relationDomainService.deleteRelation(currentUserId, relationId);
    }

    public LineageGraphResponse graph(List<Long> tableIds) {
        return relationDomainService.buildGraph(tableIds);
    }
}
