package com.ddh.agent.domain.model.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("table_columns")
public class TableColumn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tableId;
    private String columnName;
    private String dataType;
    private String comment;
    private Integer sortOrder;
}
