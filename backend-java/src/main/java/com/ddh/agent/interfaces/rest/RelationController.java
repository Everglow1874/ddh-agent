package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.service.RelationAppService;
import com.ddh.agent.interfaces.dto.request.RelationSaveRequest;
import com.ddh.agent.interfaces.dto.response.LineageGraphResponse;
import com.ddh.agent.interfaces.dto.response.RelationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 表关系（全局维度）。 */
@RestController
@RequestMapping("/api")
public class RelationController {

    @Autowired private RelationAppService relationAppService;

    @GetMapping("/relations")
    public List<RelationResponse> list(Authentication auth) {
        return relationAppService.list(Long.valueOf(auth.getName()));
    }

    @PostMapping("/relations")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody RelationSaveRequest req, Authentication auth) {
        Long id = relationAppService.create(Long.valueOf(auth.getName()), req);
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        return result;
    }

    @PutMapping("/relations/{relationId}")
    public void update(@PathVariable Long relationId,
                       @RequestBody RelationSaveRequest req,
                       Authentication auth) {
        relationAppService.update(Long.valueOf(auth.getName()), relationId, req);
    }

    @DeleteMapping("/relations/{relationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long relationId, Authentication auth) {
        relationAppService.delete(Long.valueOf(auth.getName()), relationId);
    }

    @PostMapping("/relations/graph")
    public LineageGraphResponse graph(@RequestBody Map<String, List<Long>> body, Authentication auth) {
        return relationAppService.graph(body.getOrDefault("tableIds", Collections.emptyList()));
    }
}
