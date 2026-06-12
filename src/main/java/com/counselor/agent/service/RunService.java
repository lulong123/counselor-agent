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

import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private double temperature;

    @Value("${counselor.search.enabled:false}")
    private boolean searchEnabled;

    private final RestClient.Builder restClientBuilder;

    public RunService(TaskRepository taskRepository, ThreadRepository threadRepository,
                      MessageRepository messageRepository, ChiefAgent chiefAgent,
                      AgentRouter router, ChatClient chatClient,
                      WebSearchTool webSearchTool, WebFetchTool webFetchTool,
                      ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.taskRepository = taskRepository;
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.chiefAgent = chiefAgent;
        this.router = router;
        this.chatClient = chatClient;
        this.webSearchTool = webSearchTool;
        this.webFetchTool = webFetchTool;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    private static final java.util.concurrent.atomic.AtomicLong runCounter = new java.util.concurrent.atomic.AtomicLong(0);

    public void processRun(String threadId, String teacherId, String userInput, boolean deepThinking, SseEmitter emitter) {
        long runId = runCounter.incrementAndGet();
        long startTime = System.currentTimeMillis();
        log.info("[RUN-{}] processRun START — threadId={}, inputLen={}, deepThinking={}", runId, threadId, userInput.length(), deepThinking);
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

        // Track emitter lifecycle — prevent writes after client disconnects
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        emitter.onCompletion(() -> {
            emitterClosed.set(true);
            log.info("[RUN-{}] SSE emitter COMPLETED — elapsed={}ms", runId, System.currentTimeMillis() - startTime);
        });
        emitter.onError(e -> {
            emitterClosed.set(true);
            log.warn("[RUN-{}] SSE emitter ERROR — elapsed={}ms, error={}", runId, System.currentTimeMillis() - startTime, e.getMessage());
        });
        emitter.onTimeout(() -> {
            emitterClosed.set(true);
            log.warn("[RUN-{}] SSE emitter TIMEOUT — elapsed={}ms", runId, System.currentTimeMillis() - startTime);
        });

        // Heartbeat: 每 15 秒发一个 SSE 注释，防止中间网络设备因空闲断开连接
        AtomicBoolean heartbeatActive = new AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicInteger heartbeatCount = new java.util.concurrent.atomic.AtomicInteger(0);
        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> heartbeatFuture = heartbeat.scheduleAtFixedRate(() -> {
            try {
                if (heartbeatActive.get()) {
                    emitter.send(SseEmitter.event().comment(""));
                    int count = heartbeatCount.incrementAndGet();
                    if (count % 4 == 1) { // 每 60 秒打一次
                        log.info("[RUN-{}] heartbeat OK — #{}, elapsed={}ms", runId, count, System.currentTimeMillis() - startTime);
                    }
                }
            } catch (Exception e) {
                heartbeatActive.set(false);
                log.warn("[RUN-{}] heartbeat FAILED — #{}, error={}, elapsed={}ms", runId, heartbeatCount.get() + 1, e.getMessage(), System.currentTimeMillis() - startTime);
            }
        }, 15, 15, TimeUnit.SECONDS);

        try {
            sendEvent(emitter, "metadata", Map.of("run_id", run.getId(), "thread_id", threadId));

            // Phase 0: Classification — fast, non-streaming, with timeout protection
            log.info("[RUN-{}] Calling chiefAgent.analyze()...", runId);
            long classifyStart = System.currentTimeMillis();
            TaskIntent intent;
            try {
                intent = java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> chiefAgent.analyze(userInput))
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("[RUN-{}] chiefAgent.analyze() TIMEOUT after 30s — falling back to chief", runId);
                intent = new TaskIntent(null, 0, TaskIntent.RISK_LOW, "分类超时，降级为通用模式");
            } catch (Exception e) {
                log.error("[RUN-{}] chiefAgent.analyze() FAILED — falling back to chief: {}", runId, e.getMessage());
                intent = new TaskIntent(null, 0, TaskIntent.RISK_LOW, "分类异常: " + e.getMessage());
            }
            log.info("[RUN-{}] Classification done — agentId={}, elapsed={}ms", runId, intent.agentId(), System.currentTimeMillis() - classifyStart);
            run.setIntent(intent.agentId()); run.setAgentId(intent.agentId()); run.setRisk(intent.risk());
            run.setThinking(intent.reasoning());

            String systemPrompt;
            List<FunctionCallback> tools = new ArrayList<>();
            if (searchEnabled) {
                tools.add(webSearchTool);
                tools.add(webFetchTool);
            }

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
                    runToolLoop(systemPrompt, userInput, tools, run, threadId, teacherId, deepThinking, emitter, runId, startTime);
                    return;
                }
            }

            // Chief fallback
            run.setAgentId("chief"); run.setRunName("枢衡-通用");
            taskRepository.save(run);
            sendEvent(emitter, "stage", Map.of("stage", "STREAMING", "agent", "chief", "agentName", "枢衡"));

            runToolLoop(FALLBACK_PROMPT, userInput, tools, run, threadId, teacherId, deepThinking, emitter, runId, startTime);

        } catch (Exception e) {
            log.error("Run failed: runId={}", run.getId(), e);
            failRun(run, emitter, e);
        } finally {
            heartbeatActive.set(false);
            heartbeatFuture.cancel(false);
            heartbeat.shutdown();
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
                              String threadId, String teacherId, boolean deepThinking, SseEmitter emitter,
                              long runId, long startTime) {
        StringBuilder fullResponse = new StringBuilder();
        StringBuilder fullReasoning = new StringBuilder();
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new UserMessage(userInput));
        boolean toolsUsed = false;
        String lastResponseText = null;

        String toolHint = "\n\n当前日期: " + java.time.LocalDate.now().toString() + "\n\n";
        if (searchEnabled) {
            toolHint += """

                    ⚠️ 工具使用原则（必须遵守）：
                    - 你的知识足以回答绝大多数辅导员工作问题（班会方案、谈心话术、通知起草、学业分析等），**优先直接回答，不要搜索**
                    - **仅以下情况可搜索**：用户明确要求查询最新政策法规、实时新闻事件、近期数据
                    - 即使使用搜索，也**最多1次**
                    - 不需要搜索的问题绝不搜索""";
        }

        String modelName = deepThinking ? "deepseek-v4-pro" : "deepseek-v4-flash";
        log.info("[RUN-{}] runToolLoop START — model={}, deepThinking={}, tools={}", runId, modelName, deepThinking, tools.size());

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
                    prompt, tools, modelName, deepThinking, emitter, fullReasoning, runId);

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
                        streamPrompt, List.of(), modelName, deepThinking, emitter, fullReasoning, runId);
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
            run.getRisk(), fullResponse.toString(), emitter, fullReasoning.toString(),
            runId, startTime);
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
            SseEmitter emitter, StringBuilder reasoningSink, long runId) {
        StringBuilder fullText = new StringBuilder();
        LinkedHashMap<Integer, ToolCallAccumulator> tcAccum = new LinkedHashMap<>();

        long sseStart = System.currentTimeMillis();
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
            body.put("temperature", temperature);
            body.put("reasoning_effort", deepThinking ? "medium" : "low");

            String requestJson = objectMapper.writeValueAsString(body);
            log.info("[SSE-{}] Raw SSE call — model={}, bodyLen={}", runId, model, requestJson.length());

            // Use JDK HttpClient (not HttpURLConnection) — works reliably on Alpine
            // 30s connect timeout to handle CDN IP variations and slow routes
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
            factory.setReadTimeout(Duration.ofSeconds(600));
            RestClient rawClient = restClientBuilder.requestFactory(factory).build();

            // Exponential backoff retries: up to 4 attempts (2s, 4s, 8s delays)
            Exception lastEx = null;
            int maxAttempts = 4;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                // On retry, force DNS re-resolution by re-creating HttpClient
                if (attempt > 0) {
                    int delaySec = (int) Math.pow(2, attempt); // 2s, 4s, 8s
                    log.info("DeepSeek retry attempt {}/{} after {}s...", attempt + 1, maxAttempts, delaySec);
                    try { Thread.sleep(delaySec * 1000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    // Re-create factory to get fresh DNS resolution
                    factory = new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
                    factory.setReadTimeout(Duration.ofSeconds(600));
                    rawClient = restClientBuilder.requestFactory(factory).build();
                }
                try {
                    rawClient.post()
                        .uri("https://api.deepseek.com/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer " + deepseekApiKey)
                        .body(requestJson)
                        .exchange((req, resp) -> {
                    log.info("[SSE-{}] HTTP connected — status={}, elapsed={}ms", runId, resp.getStatusCode().value(), System.currentTimeMillis() - sseStart);
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        int chunkCount = 0;
                        long firstChunkTime = 0;
                        while ((line = reader.readLine()) != null) {
                            if (firstChunkTime == 0) firstChunkTime = System.currentTimeMillis();
                            if (line.isBlank()) continue;
                            if (!line.startsWith("data:")) continue;
                            chunkCount++;
                            String json = line.substring(5).trim();
                            if (json.equals("[DONE]")) {
                                log.info("[SSE-{}] stream DONE — chunks={}, firstChunkLatency={}ms, total={}ms", runId, chunkCount, firstChunkTime > 0 ? firstChunkTime - sseStart : -1, System.currentTimeMillis() - sseStart);
                                break;
                            }

                            try {
                                var root = objectMapper.readTree(json);
                                var choices = root.path("choices");
                                if (!choices.isArray() || choices.isEmpty()) continue;
                                var delta = choices.get(0).path("delta");

                                // Reasoning → forward to frontend in real time
                                var rc = delta.path("reasoning_content");
                                if (!rc.isMissingNode() && !rc.isNull() && !rc.asText().isEmpty()) {
                                    reasoningSink.append(rc.asText());
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
                    lastEx = null;
                    break; // success
                } catch (Exception e) {
                    lastEx = e;
                    log.warn("DeepSeek API error (attempt {}/{}): {} — {}", attempt + 1, maxAttempts,
                        e.getClass().getSimpleName(), e.getMessage());
                }
            }
            if (lastEx != null) {
                log.error("DeepSeek API unreachable after {} attempts", maxAttempts);
                throw new RuntimeException("DeepSeek API 连接失败，已重试 " + maxAttempts + " 次。请稍后重试。", lastEx);
            }

        } catch (Exception e) {
            // Catch-all: JsonProcessingException from serialization, RuntimeException from retry exhaustion
            log.error("Raw SSE call failed", e);
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("DeepSeek API 通信异常: " + e.getMessage(), e);
        }

        // Build tool call list
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (var acc : tcAccum.values()) {
            if (acc.id != null) {
                toolCalls.add(new AssistantMessage.ToolCall(
                    acc.id, "function", acc.name != null ? acc.name : "", acc.args.toString()));
            }
        }

        log.info("[SSE-{}] Raw SSE done — text={} chars, toolCalls={}, elapsed={}ms", runId, fullText.length(), toolCalls.size(), System.currentTimeMillis() - sseStart);
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
                           String agentId, String risk, String fullResponse, SseEmitter emitter,
                           String reasoningContent, long runId, long startTime) {
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[RUN-{}] finishRun — agent={}, responseLen={}, reasoningLen={}, elapsed={}ms",
            runId, agentId, fullResponse.length(), reasoningContent.length(), elapsed);
        run.setStatus(RunStatus.success);

        // Only save the full CoT reasoning, skip short classification text
        if (!reasoningContent.isBlank()) {
            run.setThinking(reasoningContent);
        }
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
        } catch (IOException ex) {
            log.warn("[SEND] IO error for event {} — {}", name, ex.getMessage());
        } catch (IllegalStateException ex) {
            // emitter already completed — client disconnected, expected
            log.warn("[SEND] emitter closed for event {}", name);
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
