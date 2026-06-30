package com.ddh.agent.application.service;

import com.ddh.agent.domain.model.dialect.DialectFunctionRule;
import com.ddh.agent.domain.model.dialect.DialectKbRepository;
import com.ddh.agent.domain.model.dialect.DialectTypeRule;
import com.ddh.agent.interfaces.dto.request.DialectFunctionRuleRequest;
import com.ddh.agent.interfaces.dto.request.DialectTypeRuleRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** 方言知识库应用服务（沿用现状：不做管理员角色校验）。 */
@Service
public class DialectKbAppService {

    @Autowired private DialectKbRepository repository;

    // ============ 类型规则 ============

    public List<DialectTypeRule> listTypeRules() {
        return repository.listTypeRules();
    }

    public DialectTypeRule createType(DialectTypeRuleRequest req) {
        if (!StringUtils.hasText(req.getTypeName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "类型名不能为空");
        }
        DialectTypeRule rule = new DialectTypeRule();
        applyType(rule, req);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        if (rule.getEnabled() == null) rule.setEnabled(1);
        if (rule.getSortOrder() == null) rule.setSortOrder(0);
        return repository.saveType(rule);
    }

    public DialectTypeRule updateType(Long id, DialectTypeRuleRequest req) {
        DialectTypeRule rule = repository.findTypeById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "类型规则不存在"));
        applyType(rule, req);
        rule.setUpdatedAt(LocalDateTime.now());
        return repository.saveType(rule);
    }

    public void deleteType(Long id) {
        repository.findTypeById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "类型规则不存在"));
        repository.deleteType(id);
    }

    private void applyType(DialectTypeRule rule, DialectTypeRuleRequest req) {
        if (req.getTypeName() != null) rule.setTypeName(req.getTypeName().trim());
        rule.setAllowedForms(req.getAllowedForms());
        rule.setRoundingRule(req.getRoundingRule());
        rule.setPlatformSyntax(req.getPlatformSyntax());
        rule.setNote(req.getNote());
        if (req.getEnabled() != null) rule.setEnabled(req.getEnabled());
        if (req.getSortOrder() != null) rule.setSortOrder(req.getSortOrder());
    }

    // ============ 内部函数规则 ============

    public List<DialectFunctionRule> listFunctionRules() {
        return repository.listFunctionRules();
    }

    public DialectFunctionRule createFunction(DialectFunctionRuleRequest req) {
        if (!StringUtils.hasText(req.getFunctionName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "函数名不能为空");
        }
        DialectFunctionRule rule = new DialectFunctionRule();
        applyFunction(rule, req);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        if (rule.getEnabled() == null) rule.setEnabled(1);
        if (rule.getSortOrder() == null) rule.setSortOrder(0);
        return repository.saveFunction(rule);
    }

    public DialectFunctionRule updateFunction(Long id, DialectFunctionRuleRequest req) {
        DialectFunctionRule rule = repository.findFunctionById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "函数规则不存在"));
        applyFunction(rule, req);
        rule.setUpdatedAt(LocalDateTime.now());
        return repository.saveFunction(rule);
    }

    public void deleteFunction(Long id) {
        repository.findFunctionById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "函数规则不存在"));
        repository.deleteFunction(id);
    }

    private void applyFunction(DialectFunctionRule rule, DialectFunctionRuleRequest req) {
        if (req.getFunctionName() != null) rule.setFunctionName(req.getFunctionName().trim());
        rule.setSignature(req.getSignature());
        rule.setDescription(req.getDescription());
        rule.setExample(req.getExample());
        rule.setNote(req.getNote());
        if (req.getEnabled() != null) rule.setEnabled(req.getEnabled());
        if (req.getSortOrder() != null) rule.setSortOrder(req.getSortOrder());
    }
}
