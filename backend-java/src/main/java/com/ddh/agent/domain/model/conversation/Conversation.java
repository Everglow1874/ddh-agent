package com.ddh.agent.domain.model.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("conversations")
public class Conversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    /** 1=需求分析 2=等待schema确认 3=规划步骤 4=生成SQL 5=完成 */
    private Integer state;
    private LocalDateTime createdAt;
}
