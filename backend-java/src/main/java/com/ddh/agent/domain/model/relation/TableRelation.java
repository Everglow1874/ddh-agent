package com.ddh.agent.domain.model.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 原表关系（全局维度，挂在 source_tables 之间，无 projectId）。 */
@Data
@TableName("table_relation")
public class TableRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceTableId;
    private Long targetTableId;
    private String relationType;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
