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
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private static final String FALLBACK_PROMPT = """
            你是"枢衡"，高校辅导员AI工作助手的总调度。用户当前需求未匹配到专项助手，请直接以通用辅导员助手身份回复。

            ## 你的身份
            你是经验丰富的高校辅导员工作助手，熟悉辅导员九大职责，能处理各类学生工作相关事务。

            ## 回复要求
            1. 如果用户输入是辅导员工作相关问题（即使不在九大专项范围内），请给出专业、具体的建议
            2. 如果用户输入是闲聊/问候，请友好回应，并主动列举你能帮助的事项（主题班会、谈心谈话、材料撰写、活动方案等）
            3. 如果用户输入不清晰，请通过提问澄清具体需求

            ## 输出格式
            - 使用 Markdown 格式，层次分明
            - 内容具体可操作，不泛泛而谈
            - 语言专业且亲切，符合辅导员工作语境
            - 如涉及建议/步骤，用有序列表呈现
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

                    // 真流式：逐 chunk 推送 SSE
                    StringBuilder fullResponse = new StringBuilder();
                    agent.execute(task.getContent())
                            .doOnNext(chunk -> {
                                fullResponse.append(chunk);
                                try {
                                    sendEvent(emitter, "content", Map.of("content", chunk));
                                } catch (IOException e) {
                                    throw new RuntimeException("SSE send failed", e);
                                }
                            })
                            .doOnComplete(() -> {
                                task.setResponse(fullResponse.toString());
                                task.setStatus(TaskStatus.DONE);
                                taskRepository.save(task);
                                try {
                                    sendEvent(emitter, "stage", Map.of(
                                            "stage", "DONE",
                                            "taskId", task.getId(),
                                            "agent", agent.getId()
                                    ));
                                    emitter.complete();
                                } catch (IOException e) {
                                    emitter.completeWithError(e);
                                }
                            })
                            .doOnError(e -> {
                                log.error("Agent streaming failed: taskId={}", task.getId(), e);
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
                            })
                            .subscribe();
                    return;
                } else {
                    throw new IllegalStateException("Agent not found: " + intent.agentId());
                }
            } else {
                // 未命中 — 枢衡直接回复（真流式）
                log.info("No intent matched, fallback to chief");
                sendEvent(emitter, "stage", Map.of("stage", "STREAMING", "agent", "chief",
                        "agentName", "枢衡"));

                task.setAgentId("chief");
                task.setStatus(TaskStatus.STREAMING);
                taskRepository.save(task);

                StringBuilder fullResponse = new StringBuilder();
                chatClient.prompt()
                        .system(FALLBACK_PROMPT)
                        .user(task.getContent())
                        .stream()
                        .content()
                        .doOnNext(chunk -> {
                            fullResponse.append(chunk);
                            try {
                                sendEvent(emitter, "content", Map.of("content", chunk));
                            } catch (IOException e) {
                                throw new RuntimeException("SSE send failed", e);
                            }
                        })
                        .doOnComplete(() -> {
                            task.setResponse(fullResponse.toString());
                            task.setStatus(TaskStatus.DONE);
                            taskRepository.save(task);
                            try {
                                sendEvent(emitter, "stage", Map.of("stage", "DONE", "taskId", task.getId()));
                                emitter.complete();
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        })
                        .doOnError(e -> {
                            log.error("Chief fallback streaming failed: taskId={}", task.getId(), e);
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
                        })
                        .subscribe();
                return;
            }

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
