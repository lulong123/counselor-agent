package com.counselor.agent.service;

import com.counselor.agent.agent.AgentRouter;
import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.chief.ChiefAgent;
import com.counselor.agent.model.Task;
import com.counselor.agent.model.TaskIntent;
import com.counselor.agent.model.TaskStatus;
import com.counselor.agent.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final String FALLBACK_PROMPT = """
            你是高校辅导员的AI工作助手。用户输入与九大辅导员职责的匹配度较低，请以通用助手身份简短友好地回复。
            如果用户输入是闲聊，礼貌回应并引导其提出与辅导员工作相关的需求。
            """;

    private final TaskRepository taskRepository;
    private final ChiefAgent chiefAgent;
    private final AgentRouter router;
    private final ChatClient chatClient;

    public TaskService(TaskRepository taskRepository, ChiefAgent chiefAgent,
                       AgentRouter router, ChatClient chatClient) {
        this.taskRepository = taskRepository;
        this.chiefAgent = chiefAgent;
        this.router = router;
        this.chatClient = chatClient;
    }

    public List<Task> listTasks(String teacherId) {
        return taskRepository.findByTeacherIdOrderByCreatedAtDesc(teacherId);
    }

    public Optional<Task> getTask(String id) {
        return taskRepository.findById(id);
    }

    public void processTask(Task task, SseEmitter emitter) {
        try {
            // Stage 1: 意图分析
            sendEvent(emitter, "stage", Map.of("stage", "ROUTING", "taskId", task.getId()));
            task.setStatus(TaskStatus.ROUTING);
            taskRepository.save(task);

            TaskIntent intent = chiefAgent.analyze(task.getContent());
            task.setIntent(intent.agentId());
            task.setAgentId(intent.agentId());
            task.setRisk(intent.risk());

            if (intent.isRouted()) {
                // Stage 2: 路由到子 Agent
                Optional<SubAgent> agentOpt = router.dispatch(intent.agentId());
                if (agentOpt.isPresent()) {
                    SubAgent agent = agentOpt.get();
                    log.info("Routing to agent: {} ({})", agent.getName(), agent.getId());

                    sendEvent(emitter, "stage", Map.of(
                            "stage", "STREAMING",
                            "agent", agent.getId(),
                            "agentName", agent.getName(),
                            "risk", intent.risk()
                    ));
                    task.setStatus(TaskStatus.STREAMING);
                    taskRepository.save(task);

                    // 调用 LLM 流式 → 收集完整内容
                    List<String> chunks = agent.execute(task.getContent())
                            .collectList()
                            .block();

                    String fullResponse = chunks != null ? String.join("", chunks) : "";
                    task.setResponse(fullResponse);
                    task.setStatus(TaskStatus.DONE);
                    taskRepository.save(task);

                    sendEvent(emitter, "content", Map.of("content", fullResponse));
                    sendEvent(emitter, "stage", Map.of(
                            "stage", "DONE",
                            "taskId", task.getId(),
                            "agent", agent.getId()
                    ));
                } else {
                    throw new IllegalStateException("Agent not found: " + intent.agentId());
                }
            } else {
                // 未命中 — 枢衡直接回复（用 ChatClient，不路由子 Agent）
                log.info("No intent matched, fallback to chief");
                sendEvent(emitter, "stage", Map.of("stage", "STREAMING", "agent", "chief"));

                task.setAgentId("chief");
                task.setStatus(TaskStatus.STREAMING);
                taskRepository.save(task);

                List<String> chunks = chatClient.prompt()
                        .system(FALLBACK_PROMPT)
                        .user(task.getContent())
                        .stream()
                        .content()
                        .collectList()
                        .block();

                String fullResponse = chunks != null ? String.join("", chunks) : "抱歉，我暂时无法处理这个请求。";

                String fullResponse = String.join("", chunks);
                task.setResponse(fullResponse);
                task.setStatus(TaskStatus.DONE);
                taskRepository.save(task);

                sendEvent(emitter, "content", Map.of("content", fullResponse));
                sendEvent(emitter, "stage", Map.of("stage", "DONE", "taskId", task.getId()));
            }

            emitter.complete();

        } catch (Exception e) {
            log.error("Task processing failed: taskId={}", task.getId(), e);
            task.setStatus(TaskStatus.ERROR);
            task.setResponse("处理失败: " + e.getMessage());
            taskRepository.save(task);
            try {
                sendEvent(emitter, "stage", Map.of(
                        "stage", "ERROR",
                        "taskId", task.getId(),
                        "message", e.getMessage()
                ));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }
}
