package com.ddh.agent.domain.model.conversation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("conversation_tables")
public class ConversationTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long tableId;
}
