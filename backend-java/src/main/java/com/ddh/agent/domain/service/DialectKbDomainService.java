package com.ddh.agent.domain.service;

import com.ddh.agent.domain.model.dialect.DialectFunctionRule;
import com.ddh.agent.domain.model.dialect.DialectKbRepository;
import com.ddh.agent.domain.model.dialect.DialectTypeRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 方言知识库领域服务：把启用的规则拼成可注入系统提示词的中文段落。 */
@Service
public class DialectKbDomainService {

    @Autowired private DialectKbRepository repository;

    /** 生成方言规则提示词段落；无启用规则时返回空串。 */
    public String buildPromptSection() {
        List<DialectTypeRule> types = repository.listEnabledTypeRules();
        List<DialectFunctionRule> funcs = repository.listEnabledFunctionRules();
        if (types.isEmpty() && funcs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n本平台基于 GaussDB 但存在方言差异，生成 SQL 必须遵守以下规则：")
          .append("优先使用平台内置函数；字段类型只能用允许形态，长度按规则向上取最近允许值。\n");

        if (!types.isEmpty()) {
            sb.append("\n### 平台类型规则\n");
            for (DialectTypeRule t : types) {
                sb.append("- ").append(t.getTypeName());
                if (StringUtils.hasText(t.getAllowedForms())) {
                    sb.append("：仅允许 ").append(formatForms(t.getTypeName(), t.getAllowedForms()));
                }
                if (StringUtils.hasText(t.getRoundingRule())) {
                    sb.append("；").append(t.getRoundingRule().trim());
                }
                if (StringUtils.hasText(t.getPlatformSyntax())) {
                    sb.append("；写法：").append(t.getPlatformSyntax().trim());
                }
                if (StringUtils.hasText(t.getNote())) {
                    sb.append("（").append(t.getNote().trim()).append("）");
                }
                sb.append("\n");
            }
        }

        if (!funcs.isEmpty()) {
            sb.append("\n### 平台内置函数\n");
            for (DialectFunctionRule f : funcs) {
                String head = StringUtils.hasText(f.getSignature())
                    ? f.getSignature().trim() : f.getFunctionName();
                sb.append("- ").append(head);
                if (StringUtils.hasText(f.getDescription())) {
                    sb.append("：").append(f.getDescription().trim());
                }
                if (StringUtils.hasText(f.getExample())) {
                    sb.append(" 示例：").append(f.getExample().trim());
                }
                if (StringUtils.hasText(f.getNote())) {
                    sb.append("（").append(f.getNote().trim()).append("）");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** 把 "10,50,100,1000" 拼成 "VARCHAR(10)/VARCHAR(50)/..."；若已含括号则原样使用。 */
    private String formatForms(String typeName, String allowedForms) {
        String forms = allowedForms.trim();
        if (forms.contains("(")) {
            return forms; // 已是完整写法，原样
        }
        String[] parts = forms.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append("/");
            sb.append(typeName).append("(").append(p).append(")");
        }
        return sb.length() > 0 ? sb.toString() : forms;
    }
}
