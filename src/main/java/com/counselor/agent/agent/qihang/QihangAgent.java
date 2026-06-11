package com.counselor.agent.agent.qihang;

import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.AgentPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class QihangAgent implements SubAgent {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;

    public QihangAgent(ChatClient chatClient, AgentPromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    @Override public String getId() { return "qihang"; }
    @Override public String getName() { return "启航"; }
    @Override public String getDuty() { return "就业创业"; }

    @Override
    public String getSystemPrompt() {
        return promptService.getPrompt(getId());
    }

    @Override
    public List<String> getKeywords() {
        return List.of("就业", "求职", "简历", "面试", "创业", "招聘", "职业规划");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"启航"的身份，为高校辅导员处理以下就业创业指导需求。
                请严格按照系统提示词中的结构要求输出，确保建议具体、可操作。

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
