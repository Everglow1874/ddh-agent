package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.agent.domain.model.dialect.DialectFunctionRule;
import com.ddh.agent.domain.model.dialect.DialectKbRepository;
import com.ddh.agent.domain.model.dialect.DialectTypeRule;
import com.ddh.agent.infrastructure.persistence.mapper.DialectFunctionRuleMapper;
import com.ddh.agent.infrastructure.persistence.mapper.DialectTypeRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DialectKbRepositoryImpl implements DialectKbRepository {

    @Autowired private DialectTypeRuleMapper typeMapper;
    @Autowired private DialectFunctionRuleMapper functionMapper;

    // ============ 类型规则 ============

    @Override
    public List<DialectTypeRule> listTypeRules() {
        return typeMapper.selectList(new LambdaQueryWrapper<DialectTypeRule>()
            .orderByAsc(DialectTypeRule::getSortOrder).orderByAsc(DialectTypeRule::getId));
    }

    @Override
    public List<DialectTypeRule> listEnabledTypeRules() {
        return typeMapper.selectList(new LambdaQueryWrapper<DialectTypeRule>()
            .eq(DialectTypeRule::getEnabled, 1)
            .orderByAsc(DialectTypeRule::getSortOrder).orderByAsc(DialectTypeRule::getId));
    }

    @Override
    public Optional<DialectTypeRule> findTypeById(Long id) {
        return Optional.ofNullable(typeMapper.selectById(id));
    }

    @Override
    public DialectTypeRule saveType(DialectTypeRule rule) {
        if (rule.getId() == null) {
            typeMapper.insert(rule);
        } else {
            typeMapper.updateById(rule);
        }
        return rule;
    }

    @Override
    public void deleteType(Long id) {
        typeMapper.deleteById(id);
    }

    // ============ 内部函数规则 ============

    @Override
    public List<DialectFunctionRule> listFunctionRules() {
        return functionMapper.selectList(new LambdaQueryWrapper<DialectFunctionRule>()
            .orderByAsc(DialectFunctionRule::getSortOrder).orderByAsc(DialectFunctionRule::getId));
    }

    @Override
    public List<DialectFunctionRule> listEnabledFunctionRules() {
        return functionMapper.selectList(new LambdaQueryWrapper<DialectFunctionRule>()
            .eq(DialectFunctionRule::getEnabled, 1)
            .orderByAsc(DialectFunctionRule::getSortOrder).orderByAsc(DialectFunctionRule::getId));
    }

    @Override
    public Optional<DialectFunctionRule> findFunctionById(Long id) {
        return Optional.ofNullable(functionMapper.selectById(id));
    }

    @Override
    public DialectFunctionRule saveFunction(DialectFunctionRule rule) {
        if (rule.getId() == null) {
            functionMapper.insert(rule);
        } else {
            functionMapper.updateById(rule);
        }
        return rule;
    }

    @Override
    public void deleteFunction(Long id) {
        functionMapper.deleteById(id);
    }
}
