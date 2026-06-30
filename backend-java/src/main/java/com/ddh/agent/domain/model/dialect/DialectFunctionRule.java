package com.ddh.agent.domain.model.dialect;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 方言-内部函数知识库：平台内置函数（函数名、签名、语义、示例）。 */
@Data
@TableName("dialect_function_rule")
public class DialectFunctionRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String functionName;
    private String signature;
    private String description;
    private String example;
    private String note;
    private Integer enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
