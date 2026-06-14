package com.ddh.agent.domain.model.etl;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("etl_steps")
public class EtlStep {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long jobId;
    private Integer stepOrder;
    private String stepName;
    /** 0=final table 1=temp table */
    private Integer isTempTable;
    private String sqlFilePath;
}
