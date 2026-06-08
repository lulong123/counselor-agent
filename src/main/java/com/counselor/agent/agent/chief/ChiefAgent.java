package com.counselor.agent.agent.chief;

import com.counselor.agent.agent.AgentRouter;
import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.model.TaskIntent;
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

    public ChiefAgent(ChatClient chatClient, AgentRouter router) {
        this.chatClient = chatClient;
        this.classificationPrompt = buildClassificationPrompt(router);
    }

    private String buildClassificationPrompt(AgentRouter router) {
        String agentList = router.allAgents().stream()
                .map(a -> String.format("- agentId: \"%s\", 名称: \"%s\", 职责: \"%s\", 关键词: %s",
                        a.getId(), a.getName(), a.getDuty(), String.join(", ", a.getKeywords())))
                .collect(Collectors.joining("\n"));

        return """
                你是辅导员工作台的主控助手。分析用户输入，判断属于以下哪类辅导员职责，返回对应的 agentId。

                可选 Agent:
                %s

                规则:
                1. 如果输入与辅导员工作明确相关，返回最匹配的 agentId
                2. 如果输入与辅导员工作无关（如闲聊、天气、技术问题等），agentId 设为 null
                3. 评估风险等级: low(日常事务), medium(涉及评优评奖、违纪), high(涉及学生安全、心理危机、处分)
                4. 返回 JSON 格式，不要包含其他文字:
                {"agentId":"...", "confidence":0.0-1.0, "risk":"low|medium|high", "reasoning":"简短理由"}
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
}
