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
    /** 字段长度 */
    private Integer colLength;
    /** 字段精度 */
    private Integer colPrecision;
    /** 是否分布键 0/1 */
    private Integer isDistributionKey;
    /** 是否分区键 0/1 */
    private Integer isPartitionKey;
    /** 是否主键 0/1 */
    private Integer isPrimaryKey;
    /** 是否可为空 0/1 */
    private Integer isNullable;
    /** 代码信息 */
    private String codeInfo;
    /** 缺省值 */
    private String defaultValue;
    /** 下游作业数 */
    private Integer downstreamJobCount;
}
