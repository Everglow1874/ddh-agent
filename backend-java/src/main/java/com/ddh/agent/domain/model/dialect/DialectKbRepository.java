package com.ddh.agent.domain.model.dialect;

import java.util.List;
import java.util.Optional;

/** 方言知识库仓储（类型规则 + 内部函数规则）。 */
public interface DialectKbRepository {

    // 类型规则
    List<DialectTypeRule> listTypeRules();
    List<DialectTypeRule> listEnabledTypeRules();
    Optional<DialectTypeRule> findTypeById(Long id);
    DialectTypeRule saveType(DialectTypeRule rule);
    void deleteType(Long id);

    // 内部函数规则
    List<DialectFunctionRule> listFunctionRules();
    List<DialectFunctionRule> listEnabledFunctionRules();
    Optional<DialectFunctionRule> findFunctionById(Long id);
    DialectFunctionRule saveFunction(DialectFunctionRule rule);
    void deleteFunction(Long id);
}
