package com.ddh.agent.application.assembler;

import com.ddh.agent.domain.model.conversation.*;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ConversationAssembler {

    public ConversationResponse toResponse(Conversation c, List<Long> tableIds) {
        ConversationResponse r = new ConversationResponse();
        r.setId(c.getId());
        r.setProjectId(c.getProjectId());
        r.setState(c.getState());
        r.setCreatedAt(c.getCreatedAt());
        r.setTableIds(tableIds);
        return r;
    }

    public MessageResponse toMessageResponse(Message m) {
        MessageResponse r = new MessageResponse();
        r.setId(m.getId());
        r.setConversationId(m.getConversationId());
        r.setRole(m.getRole());
        r.setContent(m.getContent());
        r.setCreatedAt(m.getCreatedAt());
        return r;
    }
}