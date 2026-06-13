package com.ddh.agent.domain.model.table;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("source_tables")
public class SourceTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    /** 1=public 2=private */
    private Integer scope;
    private Long ownerId;
    private LocalDateTime createdAt;
}
