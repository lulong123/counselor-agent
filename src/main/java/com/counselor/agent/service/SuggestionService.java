package com.counselor.agent.service;

import com.counselor.agent.model.Message;
import com.counselor.agent.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SuggestionService {

    private static final Logger log = LoggerFactory.getLogger(SuggestionService.class);

    private static final String SUGGESTION_PROMPT = """
            你是高校辅导员工作助手。根据对话上下文，生成3个追问建议，帮助辅导员继续深入工作。

            要求：
            1. 每个建议15-30字，简洁有力
            2. 建议应具体可操作，不是泛泛的"继续讨论"
            3. 覆盖不同角度（如：方案细化、关联问题、跟进措施）
            4. 语言贴近辅导员工作语境

            返回 JSON 数组格式，不要其他内容：
            ["建议1", "建议2", "建议3"]
            """;

    private final ChatClient chatClient;
    private final MessageRepository messageRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public SuggestionService(ChatClient chatClient, MessageRepository messageRepository) {
        this.chatClient = chatClient;
        this.messageRepository = messageRepository;
    }

    public List<String> generate(String threadId) {
        try {
            List<Message> recentMessages = messageRepository.findByThreadIdOrderBySeqAsc(threadId);
            String context = recentMessages.stream()
                    .map(m -> (m.getRole().equals("user") ? "辅导员：" : "助手：") + m.getContent())
                    .collect(Collectors.joining("\n"));

            String response = chatClient.prompt()
                    .system(SUGGESTION_PROMPT)
                    .user("对话上下文：\n" + context + "\n\n请生成3个追问建议。")
                    .call()
                    .content();

            String json = extractJsonArray(response);
            @SuppressWarnings("unchecked")
            List<String> suggestions = mapper.readValue(json, List.class);
            return suggestions;

        } catch (Exception e) {
            log.warn("Failed to generate suggestions for thread {}", threadId, e);
            return List.of("帮我细化这个方案", "有没有相关的注意事项？", "后续如何跟进落实？");
        }
    }

    private String extractJsonArray(String raw) {
        String trimmed = raw.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return "[]";
    }
}
