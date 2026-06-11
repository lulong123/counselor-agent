package com.counselor.agent.agent.shouwang;

import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.AgentPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class ShouwangAgent implements SubAgent {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;

    public ShouwangAgent(ChatClient chatClient, AgentPromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    @Override public String getId() { return "shouwang"; }
    @Override public String getName() { return "守望"; }
    @Override public String getDuty() { return "危机应对"; }

    @Override
    public String getSystemPrompt() {
        return promptService.getPrompt(getId());
    }

    @Override
    public List<String> getKeywords() {
        return List.of("危机", "突发事件", "安全", "报警", "冲突", "伤害");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"守望"的身份，为高校辅导员处理以下危机事件需求。
                请严格遵循安全优先原则，输出流程规范、可立即执行的方案。

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
