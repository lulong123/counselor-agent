package com.counselor.agent.agent.yunlan;

import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.AgentPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class YunlanAgent implements SubAgent {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;

    public YunlanAgent(ChatClient chatClient, AgentPromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    @Override public String getId() { return "yunlan"; }
    @Override public String getName() { return "云澜"; }
    @Override public String getDuty() { return "网络思政"; }

    @Override
    public String getSystemPrompt() {
        return promptService.getPrompt(getId());
    }

    @Override
    public List<String> getKeywords() {
        return List.of("舆情", "网络", "朋友圈", "热搜", "话题引导", "新媒体");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"云澜"的身份，为高校辅导员处理以下网络思政需求。
                请严格遵循导向原则，输出审慎、专业、可落地的方案。

                需求：
                %s
                """.formatted(userInput);
        return chatClient.prompt()
                .system(getSystemPrompt())
                .user(wrapped)
                .stream()
                .content();
    }
}
