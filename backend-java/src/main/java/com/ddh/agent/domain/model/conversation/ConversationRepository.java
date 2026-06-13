package com.ddh.agent.domain.model.conversation;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository {
    Optional<Conversation> findById(Long id);
    List<Conversation> findByProjectId(Long projectId);
    Conversation save(Conversation conversation);

    List<ConversationTable> findTablesByConversationId(Long conversationId);
    void replaceConversationTables(Long conversationId, List<Long> tableIds);

    List<Message> findMessagesByConversationId(Long conversationId);
    Message saveMessage(Message message);
}
