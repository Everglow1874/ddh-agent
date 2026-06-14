package com.ddh.agent.domain.model.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("projects")
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    /** 1=draft 2=in_progress 3=done */
    private Integer status;
    private LocalDateTime createdAt;
}
