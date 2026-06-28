package com.ddh.agent.interfaces.rest;

import com.ddh.agent.application.service.AgentAppService;
import com.ddh.agent.application.service.ConversationAppService;
import com.ddh.agent.application.service.JobAppService;
import com.ddh.agent.interfaces.dto.request.*;
import com.ddh.agent.interfaces.dto.response.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConversationController {

    @Autowired private ConversationAppService conversationAppService;
    @Autowired private AgentAppService agentAppService;
    @Autowired private JobAppService jobAppService;

    @PostMapping("/projects/{projectId}/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(@PathVariable Long projectId,
                                       @RequestBody CreateConversationRequest req,
                                       Authentication auth) {
        return conversationAppService.createConversation(
            projectId, req, Long.valueOf(auth.getName()));
    }

    @GetMapping("/projects/{projectId}/conversations")
    public List<ConversationResponse> list(@PathVariable Long projectId,
                                           Authentication auth) {
        return conversationAppService.listConversations(
            projectId, Long.valueOf(auth.getName()));
    }

    @PostMapping("/conversations/{convId}/chat")
    public Map<String, Object> chat(@PathVariable Long convId,
                                    @RequestBody ChatRequest req,
                                    Authentication auth) {
        return conversationAppService.saveUserMessage(convId, req);
    }

    @PostMapping("/conversations/{convId}/confirm-schema")
    public ConversationResponse confirmSchema(@PathVariable Long convId,
                                              @RequestBody ConfirmSchemaRequest req,
                                              Authentication auth) {
        return conversationAppService.confirmSchema(convId, req);
    }

    @PostMapping("/conversations/{convId}/confirm-steps")
    public ConversationResponse confirmSteps(@PathVariable Long convId,
                                             @RequestBody ConfirmStepsRequest req,
                                             Authentication auth) {
        return conversationAppService.confirmSteps(convId, req);
    }

    @GetMapping("/conversations/{convId}/messages")
    public List<MessageResponse> messages(@PathVariable Long convId,
                                          Authentication auth) {
        return conversationAppService.getMessages(convId);
    }

    @GetMapping("/conversations/{convId}/tables")
    public List<TableDetailResponse> convTables(@PathVariable Long convId,
                                                Authentication auth) {
        return conversationAppService.getConversationTables(convId);
    }

    @PutMapping("/conversations/{convId}/tables")
    public ConversationResponse setTables(@PathVariable Long convId,
                                          @RequestBody SetConversationTablesRequest req,
                                          Authentication auth) {
        return conversationAppService.setConversationTables(convId, req);
    }

    @PutMapping("/conversations/{convId}")
    public ConversationResponse update(@PathVariable Long convId,
                                       @RequestBody UpdateConversationRequest req,
                                       Authentication auth) {
        return conversationAppService.updateConversation(
            convId, req, Long.valueOf(auth.getName()));
    }

    @DeleteMapping("/conversations/{convId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long convId, Authentication auth) {
        conversationAppService.deleteConversation(
            convId, Long.valueOf(auth.getName()));
    }

    @GetMapping(value = "/conversations/{convId}/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long convId, Authentication auth) {
        conversationAppService.requireConversation(convId);
        return agentAppService.stream(convId);
    }

    /** 刷新页面后恢复该对话的 SQL 结果面板：返回 {job_id, steps:[{step_order, step_name, sql}]}。 */
    @GetMapping("/conversations/{convId}/job")
    public Map<String, Object> conversationJob(@PathVariable Long convId, Authentication auth) {
        conversationAppService.requireConversation(convId);
        return jobAppService.getConversationJob(convId);
    }
}