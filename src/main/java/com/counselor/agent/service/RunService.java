package com.counselor.agent.service;

import com.counselor.agent.agent.AgentRouter;
import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.chief.ChiefAgent;
import com.counselor.agent.config.ReasoningCaptureInterceptor;
import com.counselor.agent.model.Message;
import com.counselor.agent.model.RunStatus;
import com.counselor.agent.model.Task;
import com.counselor.agent.model.TaskIntent;
import com.counselor.agent.model.ThreadStatus;
import com.counselor.agent.repository.MessageRepository;
import com.counselor.agent.repository.TaskRepository;
import com.counselor.agent.repository.ThreadRepository;
import com.counselor.agent.tool.WebFetchTool;
import com.counselor.agent.tool.WebSearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class RunService {

    private static final Logger log = LoggerFactory.getLogger(RunService.class);

    private static final String FALLBACK_PROMPT = """
            你是"枢衡"，高校辅导员AI工作助手的总调度。

            ⚠️ 身份定位（最高优先级）：
            - 用户是一位大学辅导员老师，你是TA的AI工作助手，不是辅导员本人
            - 你的回答是给辅导员参考的素材、建议、话术，帮助TA更高效地完成工作
            - 绝对不能说"作为辅导员，我……"、"我会帮你联系学生……"之类的越位表述
            - 正确说法："您可以这样跟学生沟通……"、"建议您从以下几个方面入手……"

            使用 Markdown 格式，内容具体可操作，语言专业且亲切。
            """;

    private static final int MAX_TOOL_TURNS = 5;

    private final TaskRepository taskRepository;
    private final ThreadRepository threadRepository;
    private final MessageRepository messageRepository;
    private final ChiefAgent chiefAgent;
    private final AgentRouter router;
    private final ChatClient chatClient;
    private final WebSearchTool webSearchTool;
    private final WebFetchTool webFetchTool;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.api-key}")
    private String deepseekApiKey;

    public RunService(TaskRepository taskRepository, ThreadRepository threadRepository,
                      MessageRepository messageRepository, ChiefAgent chiefAgent,
                      AgentRouter router, ChatClient chatClient,
                      WebSearchTool webSearchTool, WebFetchTool webFetchTool,
                      ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.chiefAgent = chiefAgent;
        this.router = router;
        this.chatClient = chatClient;
        this.webSearchTool = webSearchTool;
        this.webFetchTool = webFetchTool;
        this.objectMapper = objectMapper;
    }

    public void processRun(String threadId, String teacherId, String userInput, boolean deepThinking, SseEmitter emitter) {
        com.counselor.agent.model.Thread thread = threadRepository.findById(threadId)
            .orElseGet(() -> {
                com.counselor.agent.model.Thread t = new com.counselor.agent.model.Thread();
                t.setId(threadId);
                t.setTeacherId(teacherId);
                t.setTitle(null);
                t.setStatus(ThreadStatus.idle);
                threadRepository.save(t);
                log.info("Thread auto-created: id={}", threadId);
                return t;
            });
        thread.setStatus(ThreadStatus.busy);
        threadRepository.save(thread);

        Task run = new Task();
        run.setId(UUID.randomUUID().toString());
        run.setThreadId(threadId);
        run.setTeacherId(teacherId);
        run.setContent(userInput);
        run.setStatus(RunStatus.running);
        taskRepository.save(run);

        int seq = messageRepository.findMaxSeqByThreadId(threadId) + 1;
        Message userMsg = new Message();
        userMsg.setId(UUID.randomUUID().toString());
        userMsg.setThreadId(threadId); userMsg.setTeacherId(teacherId);
        userMsg.setRole("user"); userMsg.setContent(userInput); userMsg.setSeq(seq);
        messageRepository.save(userMsg);

        try {
            sendEvent(emitter, "metadata", Map.of("run_id", run.getId(), "thread_id", threadId));

            // Phase 0: Classification — fast, non-streaming
            TaskIntent intent = chiefAgent.analyze(userInput);
            run.setIntent(intent.agentId()); run.setAgentId(intent.agentId()); run.setRisk(intent.risk());
            run.setThinking(intent.reasoning());

            String systemPrompt;
            List<FunctionCallback> tools = new ArrayList<>();
            tools.add(webSearchTool);
            tools.add(webFetchTool);

            if (intent.isRouted()) {
                Optional<SubAgent> agentOpt = router.dispatch(intent.agentId());
                if (agentOpt.isPresent()) {
                    SubAgent agent = agentOpt.get();
                    run.setRunName(agent.getName() + "-" + agent.getDuty());
                    taskRepository.save(run);
                    log.info("Routing to agent: {} ({})", agent.getName(), agent.getId());

                    sendEvent(emitter, "stage", Map.of(
                        "stage", "STREAMING", "agent", agent.getId(),
                        "agentName", agent.getName(), "risk", intent.risk()));

                    systemPrompt = agent.getSystemPrompt();
                    tools.addAll(agent.getTools());
                    runToolLoop(systemPrompt, userInput, tools, run, threadId, teacherId, deepThinking, emitter);
                    return;
                }
            }

            // Chief fallback
            run.setAgentId("chief"); run.setRunName("枢衡-通用");
            taskRepository.save(run);
            sendEvent(emitter, "stage", Map.of("stage", "STREAMING", "agent", "chief", "agentName", "枢衡"));

            runToolLoop(FALLBACK_PROMPT, userInput, tools, run, threadId, teacherId, deepThinking, emitter);

        } catch (Exception e) {
            log.error("Run failed: runId={}", run.getId(), e);
            failRun(run, emitter, e);
        }
    }

    // ── Tool Loop ──
    // Phase 1: Non-streaming loop to detect/execute tool calls
    // Phase 2: If tools used → streaming call with reasoning capture; else → chunk cached text
    //
    // Agent thinking visibility:
    //   - agent_thinking events carry the model's reasoning_content (DeepSeek's CoT)
    //     extracted from ChatResponse/ChatGeneration metadata
    //   - progress events signal stage transitions between tool calls
    //   - Phase 2 streaming captures reasoning chunks from ChatGenerationMetadata

    private void runToolLoop(String systemPrompt, String userInput,
                              List<FunctionCallback> tools, Task run,
                              String threadId, String teacherId, boolean deepThinking, SseEmitter emitter) {
        StringBuilder fullResponse = new StringBuilder();
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new UserMessage(userInput));
        boolean toolsUsed = false;
        String lastResponseText = null;

        String toolHint = "\n\n当前日期: " + java.time.LocalDate.now().toString() + "\n\n"
                + "你有以下工具可用：\n"
                + "- web_search(query, max_results, recency): 搜索网络获取实时信息。recency可选值: oneDay(24h内)/oneWeek(7天内)/oneMonth(30天内)/noLimit(不限)。价格、新闻、天气等时效性内容务必用oneDay。\n"
                + "- web_fetch(url): 抓取指定网页的完整内容\n"
                + "搜索时效性内容时务必在query中加上日期，并用recency=oneDay过滤，确保结果是最新的。";

        String modelName = deepThinking ? "deepseek-v4-pro" : "deepseek-v4-flash";
        log.info("Using model: {} (deepThinking={})", modelName, deepThinking);

        OpenAiChatOptions modelOptions = OpenAiChatOptions.builder()
                .model(modelName)
                .reasoningEffort(deepThinking ? "medium" : "low")
                .build();

        try {
            // ── Phase 1: Streaming tool-calling loop ──
            // Uses .stream() so DeepSeek streams SSE chunks — the interceptor
            // captures reasoning_content in real time and forwards to frontend.
            for (int turn = 0; turn < MAX_TOOL_TURNS; turn++) {
                List<org.springframework.ai.chat.messages.Message> prompt = new ArrayList<>();
                prompt.add(new SystemMessage(systemPrompt + toolHint));
                prompt.addAll(messages);

                // Use raw RestClient SSE to get TRUE streaming from DeepSeek.
                // Spring AI's .stream() uses WebClient (no interceptor), and worse,
                // doesn't set stream=true in the request. We bypass it entirely.
                AssistantMessage aiMsg = rawStreamingCall(
                    prompt, tools, modelName, deepThinking, emitter);

                if (aiMsg.hasToolCalls()) {
                    toolsUsed = true;
                    List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

                    for (AssistantMessage.ToolCall tc : aiMsg.getToolCalls()) {
                        log.info("Tool call: {} (id={})", tc.name(), tc.id());

                        sendEvent(emitter, "tool_call", Map.of(
                            "id", tc.id(), "name", tc.name(), "args", tc.arguments()));

                        String result = executeToolCall(tc, tools);

                        sendEvent(emitter, "tool_result", Map.of(
                            "id", tc.id(), "name", tc.name(), "result", result));

                        toolResponses.add(new ToolResponseMessage.ToolResponse(
                            tc.id(), tc.name(), result));
                    }

                    messages.add(aiMsg);
                    messages.add(new ToolResponseMessage(toolResponses, Map.of()));
                    continue;
                }

                // No tool calls — save text and exit loop
                lastResponseText = aiMsg.getText();
                break;
            }

            // ── Phase 2: Generate final answer ──
            if (toolsUsed) {
                // Tools used → fresh streaming call with full context (real SSE)
                List<org.springframework.ai.chat.messages.Message> streamPrompt = new ArrayList<>();
                streamPrompt.add(new SystemMessage(systemPrompt + toolHint));
                streamPrompt.addAll(messages);

                try {
                    AssistantMessage finalMsg = rawStreamingCall(
                        streamPrompt, List.of(), modelName, deepThinking, emitter);
                    if (finalMsg.getText() != null) {
                        fullResponse.append(finalMsg.getText());
                    }
                } catch (Exception e) {
                    log.error("Phase 2 stream failed: runId={}", run.getId(), e);
                    if (fullResponse.isEmpty()) {
                        String err = "抱歉，处理请求时出错：" + e.getMessage();
                        fullResponse.append(err);
                        sendEvent(emitter, "messages-tuple",
                            List.of(Map.of("type", "ai", "content", err)));
                    }
                }
            } else if (lastResponseText != null && !lastResponseText.isBlank()) {
                // No tools used → chunk already-generated text
                fullResponse.append(lastResponseText);
                streamTextInChunks(lastResponseText, emitter);
            }

            if (fullResponse.isEmpty()) {
                fullResponse.append("抱歉，处理您的请求时遇到了问题，请稍后重试。");
                sendEvent(emitter, "messages-tuple",
                    List.of(Map.of("type", "ai", "content", fullResponse.toString())));
            }

        } catch (Exception e) {
            log.error("Tool loop failed: runId={}", run.getId(), e);
            String errMsg = "抱歉，处理请求时出错：" + e.getMessage();
            fullResponse.append(errMsg);
            sendEvent(emitter, "messages-tuple",
                List.of(Map.of("type", "ai", "content", errMsg)));
        }

        finishRun(run, threadId, teacherId,
            run.getAgentId() != null ? run.getAgentId() : "chief",
            run.getRisk(), fullResponse.toString(), emitter);
    }

    /**
     * Makes a raw SSE streaming call to DeepSeek (bypassing Spring AI) to get TRUE
     * real-time reasoning_content chunks. Processes each SSE line as it arrives,
     * forwards reasoning to the frontend, and accumulates text + tool calls.
     */
    @SuppressWarnings("unchecked")
    private AssistantMessage rawStreamingCall(
            List<org.springframework.ai.chat.messages.Message> prompt,
            List<FunctionCallback> tools, String model, boolean deepThinking,
            SseEmitter emitter) {
        StringBuilder fullText = new StringBuilder();
        LinkedHashMap<Integer, ToolCallAccumulator> tcAccum = new LinkedHashMap<>();

        try {
            // Build request body matching OpenAI/DeepSeek API format
            List<Map<String, Object>> msgs = new ArrayList<>();
            for (var pm : prompt) {
                String role = switch (pm.getMessageType()) {
                    case SYSTEM -> "system";
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case TOOL -> "tool";
                };
                if (pm instanceof AssistantMessage am && am.hasToolCalls()) {
                    // Serialize tool_calls array
                    List<Map<String, Object>> tcList = new ArrayList<>();
                    for (var tc : am.getToolCalls()) {
                        tcList.add(Map.of(
                            "id", tc.id(),
                            "type", "function",
                            "function", Map.of(
                                "name", tc.name(),
                                "arguments", tc.arguments())));
                    }
                    msgs.add(new LinkedHashMap<>(Map.of(
                        "role", "assistant",
                        "content", (Object) (am.getText() != null ? am.getText() : null),
                        "tool_calls", tcList)));
                } else if (pm instanceof ToolResponseMessage trm) {
                    // Each tool response is a separate message with tool_call_id
                    for (var tr : trm.getResponses()) {
                        msgs.add(new LinkedHashMap<>(Map.of(
                            "role", "tool",
                            "tool_call_id", tr.id(),
                            "content", (Object) (tr.responseData() != null ? tr.responseData() : ""))));
                    }
                } else {
                    msgs.add(Map.of("role", role, "content", pm.getText()));
                }
            }
            List<Map<String, Object>> toolDefs = new ArrayList<>();
            for (var tc : tools) {
                toolDefs.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                        "name", tc.getName(),
                        "description", tc.getDescription(),
                        "parameters", objectMapper.readTree(tc.getInputTypeSchema()))));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", msgs);
            if (!toolDefs.isEmpty()) body.put("tools", toolDefs);
            body.put("stream", true);
            body.put("temperature", 0.7);
            body.put("reasoning_effort", deepThinking ? "medium" : "low");

            String requestJson = objectMapper.writeValueAsString(body);
            log.info("Raw SSE call — model={}, bodyLen={}", model, requestJson.length());

            // Use a fresh RestClient (no interceptor) — we process the stream directly
            RestClient rawClient = RestClient.create();
            rawClient.post()
                .uri("https://api.deepseek.com/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + deepseekApiKey)
                .body(requestJson)
                .exchange((req, resp) -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.isBlank()) continue;
                            if (!line.startsWith("data:")) continue;
                            String json = line.substring(5).trim();
                            if (json.equals("[DONE]")) break;

                            try {
                                var root = objectMapper.readTree(json);
                                var choices = root.path("choices");
                                if (!choices.isArray() || choices.isEmpty()) continue;
                                var delta = choices.get(0).path("delta");

                                // Reasoning → forward to frontend in real time
                                var rc = delta.path("reasoning_content");
                                if (!rc.isMissingNode() && !rc.isNull() && !rc.asText().isEmpty()) {
                                    safeSend(emitter, "agent_thinking",
                                        Map.of("content", rc.asText()));
                                }

                                // Text content → accumulate + stream to frontend
                                var ct = delta.path("content");
                                if (!ct.isMissingNode() && !ct.isNull() && !ct.asText().isEmpty()) {
                                    String t = ct.asText();
                                    fullText.append(t);
                                    safeSend(emitter, "content", Map.of("content", t));
                                }

                                // Tool calls
                                var tcs = delta.path("tool_calls");
                                if (tcs.isArray()) {
                                    for (var tcNode : tcs) {
                                        int idx = tcNode.path("index").asInt(0);
                                        var fn = tcNode.path("function");
                                        var acc = tcAccum.computeIfAbsent(idx,
                                            k -> new ToolCallAccumulator());
                                        var idNode = tcNode.path("id");
                                        if (!idNode.isMissingNode()) acc.id = idNode.asText();
                                        var nm = fn.path("name");
                                        if (!nm.isMissingNode()) acc.name = nm.asText();
                                        var args = fn.path("arguments");
                                        if (!args.isMissingNode()) acc.args.append(args.asText());
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("SSE parse error: {}", e.getMessage());
                            }
                        }
                    }
                    return null;
                });

        } catch (IOException e) {
            log.error("Raw SSE call failed", e);
        }

        // Build tool call list
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (var acc : tcAccum.values()) {
            if (acc.id != null) {
                toolCalls.add(new AssistantMessage.ToolCall(
                    acc.id, "function", acc.name != null ? acc.name : "", acc.args.toString()));
            }
        }

        log.info("Raw SSE done — text={} chars, toolCalls={}", fullText.length(), toolCalls.size());
        return new AssistantMessage(fullText.toString(), Map.of(), toolCalls);
    }

    /** Accumulates tool call data from streaming SSE chunks. */
    private static class ToolCallAccumulator {
        String id, name;
        StringBuilder args = new StringBuilder();
    }

    /** Split text into small chunks and send as content SSE events */
    private void streamTextInChunks(String text, SseEmitter emitter) {
        streamTextInChunks(text, emitter, "content", 3, 15);
    }

    /** Split text into chunks and send as named SSE events with delay between each */
    private void streamTextInChunks(String text, SseEmitter emitter, String eventName,
                                     int chunkSize, int delayMs) {
        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            String chunk = text.substring(i, end);
            sendEvent(emitter, eventName, Map.of("content", chunk));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private String executeToolCall(AssistantMessage.ToolCall tc, List<FunctionCallback> tools) {
        for (FunctionCallback tool : tools) {
            if (tool.getName().equals(tc.name())) {
                try {
                    return tool.call(tc.arguments());
                } catch (Exception e) {
                    log.error("Tool execution failed: {} - {}", tc.name(), e.getMessage());
                    return "Error executing " + tc.name() + ": " + e.getMessage();
                }
            }
        }
        return "Unknown tool: " + tc.name();
    }

    // ── Finish / Fail ──

    private void finishRun(Task run, String threadId, String teacherId,
                           String agentId, String risk, String fullResponse, SseEmitter emitter) {
        run.setStatus(RunStatus.success);
        taskRepository.save(run);

        int seq = messageRepository.findMaxSeqByThreadId(threadId) + 1;
        Message aiMsg = new Message();
        aiMsg.setId(UUID.randomUUID().toString());
        aiMsg.setThreadId(threadId); aiMsg.setTeacherId(teacherId);
        aiMsg.setRole("assistant"); aiMsg.setContent(fullResponse);
        aiMsg.setAgentId(agentId); aiMsg.setRisk(risk); aiMsg.setSeq(seq);
        aiMsg.setThinking(run.getThinking());
        messageRepository.save(aiMsg);

        threadRepository.findById(threadId).ifPresent(t -> {
            t.setStatus(ThreadStatus.idle);
            if (messageRepository.countByThreadId(threadId) <= 2 && t.getTitle() == null) {
                t.setTitle(fullResponse.length() > 40 ? fullResponse.substring(0, 40) + "…" : fullResponse);
            }
            threadRepository.save(t);
        });

        safeSend(emitter, "end", Map.of("status", "success"));
        safeComplete(emitter);
    }

    private void failRun(Task run, SseEmitter emitter, Throwable e) {
        run.setStatus(RunStatus.error);
        taskRepository.save(run);
        safeSend(emitter, "error", Map.of("message", e.getMessage() != null ? e.getMessage() : "unknown"));
        safeComplete(emitter);
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        safeSend(emitter, name, data);
    }

    private void safeSend(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException ex) {
            log.warn("SSE send failed for event {}: {}", name, ex.getMessage());
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ex) {
            log.warn("SSE complete failed: {}", ex.getMessage());
        }
    }

    public List<Message> getMessages(String threadId, int beforeSeq, int limit) {
        if (beforeSeq <= 0) {
            // Initial load: most recent messages, returned in chronological order
            List<Message> msgs = messageRepository.findByThreadIdOrderBySeqDesc(
                threadId, org.springframework.data.domain.PageRequest.of(0, limit));
            java.util.Collections.reverse(msgs);
            return msgs;
        }
        return messageRepository.findByThreadIdAndSeqLessThanOrderBySeqAsc(
            threadId, beforeSeq,
            org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
