package com.ddh.agent.domain.model.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("project_tables")
public class ProjectTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long tableId;
}
