package com.counselor.agent.agent.mingli;

import com.counselor.agent.agent.SubAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class MingliAgent implements SubAgent {

    private final ChatClient chatClient;

    public MingliAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override public String getId() { return "mingli"; }
    @Override public String getName() { return "明理"; }
    @Override public String getDuty() { return "思政引领"; }

    @Override
    public String getSystemPrompt() {
        return """
                你是"明理"，思想政治教育助手。擅长设计主题班会方案、形势政策解读、社会主义核心价值观教育素材。
                输出要求：主题明确、目标清晰、活动流程可操作、预期效果可评估。
                语言正式但不刻板，适合辅导员在工作中直接使用。
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("思政", "主题教育", "班会", "价值观", "形势政策", "二十大", "爱国主义");
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
