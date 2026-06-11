package com.counselor.agent.agent.duxue;

import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.AgentPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class DuxueAgent implements SubAgent {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;

    public DuxueAgent(ChatClient chatClient, AgentPromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    @Override public String getId() { return "duxue"; }
    @Override public String getName() { return "笃学"; }
    @Override public String getDuty() { return "学风建设"; }

    @Override
    public String getSystemPrompt() {
        return promptService.getPrompt(getId());
    }

    @Override
    public List<String> getKeywords() {
        return List.of("挂科", "成绩", "学业", "考勤", "学风", "学习困难");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"笃学"的身份，为高校辅导员处理以下需求。
                请严格按照系统提示词中的结构要求输出，确保数据驱动、措施可跟踪。

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
