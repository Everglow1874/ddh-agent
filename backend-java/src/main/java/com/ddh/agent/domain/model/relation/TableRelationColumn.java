package com.ddh.agent.domain.model.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 表关系字段对（支持复合键）。 */
@Data
@TableName("table_relation_column")
public class TableRelationColumn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long relationId;
    private Long sourceColumnId;
    private Long targetColumnId;
    private Integer sortOrder;
}
