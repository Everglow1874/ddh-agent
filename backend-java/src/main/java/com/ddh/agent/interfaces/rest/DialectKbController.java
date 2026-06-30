package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.service.DialectKbAppService;
import com.ddh.agent.domain.model.dialect.DialectFunctionRule;
import com.ddh.agent.domain.model.dialect.DialectTypeRule;
import com.ddh.agent.interfaces.dto.request.DialectFunctionRuleRequest;
import com.ddh.agent.interfaces.dto.request.DialectTypeRuleRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 平台方言知识库（类型规则 + 内部函数）。沿用现状：登录用户即可维护。 */
@RestController
@RequestMapping("/api/admin/dialect")
public class DialectKbController {

    @Autowired private DialectKbAppService appService;

    // ============ 类型规则 ============

    @GetMapping("/types")
    public List<DialectTypeRule> listTypes() {
        return appService.listTypeRules();
    }

    @PostMapping("/types")
    @ResponseStatus(HttpStatus.CREATED)
    public DialectTypeRule createType(@RequestBody DialectTypeRuleRequest req) {
        return appService.createType(req);
    }

    @PutMapping("/types/{id}")
    public DialectTypeRule updateType(@PathVariable Long id, @RequestBody DialectTypeRuleRequest req) {
        return appService.updateType(id, req);
    }

    @DeleteMapping("/types/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteType(@PathVariable Long id) {
        appService.deleteType(id);
    }

    // ============ 内部函数规则 ============

    @GetMapping("/functions")
    public List<DialectFunctionRule> listFunctions() {
        return appService.listFunctionRules();
    }

    @PostMapping("/functions")
    @ResponseStatus(HttpStatus.CREATED)
    public DialectFunctionRule createFunction(@RequestBody DialectFunctionRuleRequest req) {
        return appService.createFunction(req);
    }

    @PutMapping("/functions/{id}")
    public DialectFunctionRule updateFunction(@PathVariable Long id, @RequestBody DialectFunctionRuleRequest req) {
        return appService.updateFunction(id, req);
    }

    @DeleteMapping("/functions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFunction(@PathVariable Long id) {
        appService.deleteFunction(id);
    }
}
