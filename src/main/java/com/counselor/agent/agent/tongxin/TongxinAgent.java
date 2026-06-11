package com.counselor.agent.agent.tongxin;

import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.AgentPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class TongxinAgent implements SubAgent {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;

    public TongxinAgent(ChatClient chatClient, AgentPromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    @Override public String getId() { return "tongxin"; }
    @Override public String getName() { return "同心"; }
    @Override public String getDuty() { return "党团班级"; }

    @Override
    public String getSystemPrompt() {
        return promptService.getPrompt(getId());
    }

    @Override
    public List<String> getKeywords() {
        return List.of("入党", "入团", "推优", "党支部", "团支部", "班委", "班级建设");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"同心"的身份，为高校辅导员处理以下需求。
                请严格按照系统提示词中的结构要求输出，确保政策依据准确、操作步骤清晰。

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
