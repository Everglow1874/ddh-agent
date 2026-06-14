package com.ddh.agent.application.service;

import com.ddh.agent.application.assembler.ConversationAssembler;
import com.ddh.agent.application.assembler.TableAssembler;
import com.ddh.agent.domain.model.conversation.*;
import com.ddh.agent.domain.model.project.ProjectRepository;
import com.ddh.agent.domain.model.table.*;
import com.ddh.agent.interfaces.dto.request.*;
import com.ddh.agent.interfaces.dto.response.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConversationAppService {

    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private SourceTableRepository sourceTableRepository;
    @Autowired private ConversationAssembler assembler;
    @Autowired private TableAssembler tableAssembler;
    @Autowired private ObjectMapper objectMapper;

    public ConversationResponse createConversation(Long projectId,
                                                   CreateConversationRequest req,
                                                   Long ownerId) {
        requireOwnedProject(projectId, ownerId);

        Conversation conv = new Conversation();
        conv.setProjectId(projectId);
        conv.setState(1);
        conv.setCreatedAt(LocalDateTime.now());
        conversationRepository.save(conv);

        List<Long> tableIds = req.getTableIds() != null ? req.getTableIds() : Collections.emptyList();
        if (!tableIds.isEmpty()) {
            conversationRepository.replaceConversationTables(conv.getId(), tableIds);
        }
        return assembler.toResponse(conv, tableIds);
    }

    public List<ConversationResponse> listConversations(Long projectId, Long ownerId) {
        requireOwnedProject(projectId, ownerId);
        return conversationRepository.findByProjectId(projectId).stream()
            .map(c -> assembler.toResponse(c, tableIdsOf(c.getId())))
            .collect(Collectors.toList());
    }

    public List<MessageResponse> getMessages(Long convId) {
        requireConversation(convId);
        return conversationRepository.findMessagesByConversationId(convId).stream()
            .map(assembler::toMessageResponse).collect(Collectors.toList());
    }

    public List<TableDetailResponse> getConversationTables(Long convId) {
        requireConversation(convId);
        return sourceTableRepository.findWithColumnsByConversationId(convId).stream()
            .map(tableAssembler::toDetailResponse).collect(Collectors.toList());
    }

    public ConversationResponse setConversationTables(Long convId,
                                                      SetConversationTablesRequest req) {
        Conversation conv = requireConversation(convId);
        List<Long> tableIds = req.getTableIds() != null ? req.getTableIds() : Collections.emptyList();
        conversationRepository.replaceConversationTables(convId, tableIds);
        return assembler.toResponse(conv, tableIds);
    }

    public Map<String, Object> saveUserMessage(Long convId, ChatRequest req) {
        requireConversation(convId);
        Message msg = new Message();
        msg.setConversationId(convId);
        msg.setRole("user");
        msg.setContent(req.getMessage());
        msg.setCreatedAt(LocalDateTime.now());
        conversationRepository.saveMessage(msg);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("conversation_id", convId);
        return result;
    }

    public ConversationResponse confirmSchema(Long convId, ConfirmSchemaRequest req) {
        Conversation conv = requireConversation(convId);
        if (conv.getState() != 1 && conv.getState() != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot confirm schema in state " + conv.getState());
        }
        conv.setState(3);
        conversationRepository.save(conv);

        String colJson;
        try {
            colJson = objectMapper.writeValueAsString(req.getColumns());
        } catch (JsonProcessingException e) {
            colJson = "[]";
        }
        saveUserMessageRaw(convId,
            "目标表结构已确认。目标表：" + req.getTargetTable() + "，字段：" + colJson + "。请规划ETL步骤。");

        return assembler.toResponse(conv, tableIdsOf(convId));
    }

    public ConversationResponse confirmSteps(Long convId, ConfirmStepsRequest req) {
        Conversation conv = requireConversation(convId);
        if (conv.getState() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cannot confirm steps in state " + conv.getState());
        }
        conv.setState(4);
        conversationRepository.save(conv);

        String stepsJson;
        try {
            stepsJson = objectMapper.writeValueAsString(req.getSteps());
        } catch (JsonProcessingException e) {
            stepsJson = "[]";
        }
        saveUserMessageRaw(convId,
            "ETL步骤已确认。步骤计划：" + stepsJson + "。请为每个步骤生成GaussDB SQL。");

        return assembler.toResponse(conv, tableIdsOf(convId));
    }

    public Conversation requireConversation(Long convId) {
        return conversationRepository.findById(convId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private void requireOwnedProject(Long projectId, Long ownerId) {
        projectRepository.findById(projectId)
            .filter(p -> p.getOwnerId().equals(ownerId))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Project not found"));
    }

    private List<Long> tableIdsOf(Long convId) {
        return conversationRepository.findTablesByConversationId(convId).stream()
            .map(ConversationTable::getTableId).collect(Collectors.toList());
    }

    private void saveUserMessageRaw(Long convId, String content) {
        Message msg = new Message();
        msg.setConversationId(convId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        conversationRepository.saveMessage(msg);
    }
}