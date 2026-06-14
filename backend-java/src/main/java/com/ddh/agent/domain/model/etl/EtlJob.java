package com.ddh.agent.domain.model.etl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("etl_jobs")
public class EtlJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long conversationId;
    private String targetTable;
    /** JSON: [{"name":"col","type":"VARCHAR","comment":"..."}] */
    private String targetSchema;
    private String planMdPath;
    private LocalDateTime createdAt;
}
