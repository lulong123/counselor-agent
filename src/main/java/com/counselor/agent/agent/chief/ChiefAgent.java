package com.counselor.agent.agent.chief;

import com.counselor.agent.agent.AgentPromptService;
import com.counselor.agent.agent.AgentRouter;
import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.model.TaskIntent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ChiefAgent {

    private static final Logger log = LoggerFactory.getLogger(ChiefAgent.class);

    private final ChatClient chatClient;
    private final String classificationPrompt;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChiefAgent(ChatClient chatClient, AgentRouter router, AgentPromptService promptService) {
        this.chatClient = chatClient;
        this.classificationPrompt = buildClassificationPrompt(router, promptService);
    }

    private String buildClassificationPrompt(AgentRouter router, AgentPromptService promptService) {
        String agentList = router.allAgents().stream()
                .map(a -> String.format("- agentId: \"%s\", 名称: \"%s\", 职责: \"%s\", 关键词: %s",
                        a.getId(), a.getName(), a.getDuty(), String.join(", ", a.getKeywords())))
                .collect(Collectors.joining("\n"));

        String template = promptService.getChiefPrompt();
        if (template.isBlank()) {
            log.warn("Chief prompt not found, using fallback");
            return buildFallbackPrompt(agentList);
        }
        return template.replace("{agent_list}", agentList);
    }

    private String buildFallbackPrompt(String agentList) {
        return """
                你是辅导员工作台的主控调度助手"枢衡"。分析用户输入，判断属于以下哪类辅导员职责，返回对应的 agentId。

                可选专项助手:
                %s

                分类规则（按优先级）:
                1. **班会/团日/主题教育/思政活动/形势政策** → "mingli" (明理·思政引领)
                2. **入党/入团/推优/班委选举/班级建设** → "tongxin" (同心·党团班级)
                3. **成绩分析/挂科/学业预警/帮辅/考勤** → "duxue" (笃学·学风建设)
                4. **通知起草/材料收发/评奖评优/台账** → "youxu" (有序·日常事务)
                5. **心理/情绪/焦虑/抑郁/谈心/疏导** → "xinqing" (心晴·心理支持)
                6. **舆情/网络话题/新媒体/群公告** → "yunlan" (云澜·网络思政)
                7. **危机/突发事件/安全/报警/冲突** → "shouwang" (守望·危机应对)
                8. **就业/简历/面试/创业/职业规划** → "qihang" (启航·就业创业)
                9. **调研/课题/问卷/论文/案例撰写** → "yanxing" (研行·实践研究)
                10. 如果输入与辅导员工作完全无关（闲聊天气、技术问题等），agentId 设为 null，不路由到任何专项助手

                风险评估:
                - low: 日常事务（通知、材料、一般咨询）
                - medium: 涉及评优评奖、违纪处理、成绩排名、舆情
                - high: 涉及学生安全、心理危机、自伤自杀、暴力冲突、违法事件

                返回 JSON 格式，不包含任何其他文字:
                {"agentId":"mingli", "confidence":0.95, "risk":"low", "reasoning":"用户请求设计班会方案，匹配思政引领"}
                {"agentId":null, "confidence":0.1, "risk":"low", "reasoning":"用户输入为闲聊，与辅导员工作无关"}
                """.formatted(agentList);
    }

    public TaskIntent analyze(String userInput) {
        try {
            String response = chatClient.prompt()
                    .system(classificationPrompt)
                    .user(userInput)
                    .call()
                    .content();

            log.debug("ChiefAgent raw response: {}", response);

            if (response == null || response.isBlank()) {
                return new TaskIntent(null, 0, TaskIntent.RISK_LOW, "LLM 返回空");
            }

            String json = extractJson(response);
            TaskIntent intent = objectMapper.readValue(json, TaskIntent.class);
            log.info("Intent: agentId={}, confidence={}, risk={}", intent.agentId(), intent.confidence(), intent.risk());
            return intent;

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse ChiefAgent response: {}", e.getMessage());
            return new TaskIntent(null, 0, TaskIntent.RISK_LOW, "JSON 解析失败");
        } catch (Exception e) {
            log.error("ChiefAgent analysis failed", e);
            return new TaskIntent(null, 0, TaskIntent.RISK_LOW, "分析异常: " + e.getMessage());
        }
    }

    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n");
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    public TaskIntent analyzeWithStream(String userInput, SseEmitter emitter) {
        StringBuilder buf = new StringBuilder();
        try {
            chatClient.prompt()
                    .system(classificationPrompt)
                    .user(userInput)
                    .stream()
                    .content()
                    .doOnNext(chunk -> {
                        buf.append(chunk);
                        try {
                            emitter.send(SseEmitter.event()
                                .name("thinking")
                                .data(Map.of("content", chunk)));
                        } catch (IOException e) {
                            // client disconnected
                        }
                    })
                    .blockLast();

            String response = buf.toString();
            log.debug("ChiefAgent streaming response: {}", response);

            if (response.isBlank()) {
                return new TaskIntent(null, 0, TaskIntent.RISK_LOW, "LLM 返回空");
            }

            String json = extractJson(response);
            TaskIntent intent = objectMapper.readValue(json, TaskIntent.class);
            log.info("Intent: agentId={}, confidence={}, risk={}", intent.agentId(), intent.confidence(), intent.risk());
            return intent;

        } catch (Exception e) {
            log.error("ChiefAgent streaming analysis failed", e);
            // Fallback: try to parse what we got so far
            if (buf.length() > 0) {
                try {
                    String json = extractJson(buf.toString());
                    return objectMapper.readValue(json, TaskIntent.class);
                } catch (Exception ex) {
                    // ignore
                }
            }
            return new TaskIntent(null, 0, TaskIntent.RISK_LOW, "流式分析异常: " + e.getMessage());
        }
    }

}
