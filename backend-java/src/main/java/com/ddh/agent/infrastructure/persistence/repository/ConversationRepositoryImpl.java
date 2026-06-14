package com.ddh.agent.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.agent.domain.model.conversation.*;
import com.ddh.agent.infrastructure.persistence.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class ConversationRepositoryImpl implements ConversationRepository {

    @Autowired private ConversationMapper conversationMapper;
    @Autowired private ConversationTableMapper conversationTableMapper;
    @Autowired private MessageMapper messageMapper;

    @Override
    public Optional<Conversation> findById(Long id) {
        return Optional.ofNullable(conversationMapper.selectById(id));
    }

    @Override
    public List<Conversation> findByProjectId(Long projectId) {
        return conversationMapper.selectList(
            new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getProjectId, projectId)
                .orderByDesc(Conversation::getCreatedAt));
    }

    @Override
    public Conversation save(Conversation conversation) {
        if (conversation.getId() == null) {
            conversationMapper.insert(conversation);
        } else {
            conversationMapper.updateById(conversation);
        }
        return conversation;
    }

    @Override
    public List<ConversationTable> findTablesByConversationId(Long conversationId) {
        return conversationTableMapper.selectList(
            new LambdaQueryWrapper<ConversationTable>()
                .eq(ConversationTable::getConversationId, conversationId));
    }

    @Override
    public void replaceConversationTables(Long conversationId, List<Long> tableIds) {
        conversationTableMapper.delete(
            new LambdaQueryWrapper<ConversationTable>()
                .eq(ConversationTable::getConversationId, conversationId));
        tableIds.forEach(tid -> {
            ConversationTable ct = new ConversationTable();
            ct.setConversationId(conversationId);
            ct.setTableId(tid);
            conversationTableMapper.insert(ct);
        });
    }

    @Override
    public List<Message> findMessagesByConversationId(Long conversationId) {
        return messageMapper.selectList(
            new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .orderByAsc(Message::getId));
    }

    @Override
    public Message saveMessage(Message message) {
        messageMapper.insert(message);
        return message;
    }
}
