package com.counselor.agent.agent.xinqing;

import com.counselor.agent.agent.SubAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class XinqingAgent implements SubAgent {

    private final ChatClient chatClient;

    public XinqingAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override public String getId() { return "xinqing"; }
    @Override public String getName() { return "心晴"; }
    @Override public String getDuty() { return "心理支持"; }

    @Override
    public String getSystemPrompt() {
        return """
                你是"心晴"，心理支持助手。识别学生情绪状态、提供谈心谈话话术、判断是否需要转介心理咨询中心。

                重要安全边界：
                - 你**不替代**专业心理诊断
                - 发现自杀意念、自伤行为、严重精神症状等危机信号时，必须明确建议"立即转介学校心理咨询中心，必要时陪同就医"
                - 你的输出是给辅导员老师参考的话术和建议，不是给学生的直接对话
                - 语气温和但专业，不制造恐慌，也不轻描淡写
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("心理", "情绪", "失眠", "焦虑", "抑郁", "谈心", "疏导");
    }

    @Override
    public Flux<String> execute(String userInput) {
        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(userInput)
                .stream()
                .content();
    }
}
