package com.ddh.agent.domain.model.dialect;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 方言-类型知识库：标准类型与平台类型的差异，尤其允许的长度/精度形态与取整规则。 */
@Data
@TableName("dialect_type_rule")
public class DialectTypeRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 类型名，如 VARCHAR */
    private String typeName;
    /** 允许的长度/精度形态，如 10,50,100,1000 */
    private String allowedForms;
    /** 取整规则，如 向上取最近允许值 */
    private String roundingRule;
    /** 平台写法（与标准不同时填） */
    private String platformSyntax;
    private String note;
    private Integer enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
