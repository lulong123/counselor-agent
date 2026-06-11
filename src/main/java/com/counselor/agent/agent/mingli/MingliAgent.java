package com.counselor.agent.agent.mingli;

import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.AgentPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class MingliAgent implements SubAgent {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;

    public MingliAgent(ChatClient chatClient, AgentPromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    @Override public String getId() { return "mingli"; }
    @Override public String getName() { return "明理"; }
    @Override public String getDuty() { return "思政引领"; }

    @Override
    public String getSystemPrompt() {
        return promptService.getPrompt(getId());
    }

    @Override
    public List<String> getKeywords() {
        return List.of("思政", "主题教育", "班会", "价值观", "形势政策", "二十大", "爱国主义");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"明理"的身份，为高校辅导员处理以下需求。
                请严格按照系统提示词中的结构要求输出，确保内容详实、可直接使用。

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
