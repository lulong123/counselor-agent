package com.counselor.agent.agent.xinqing;

import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.AgentPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class XinqingAgent implements SubAgent {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;

    public XinqingAgent(ChatClient chatClient, AgentPromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    @Override public String getId() { return "xinqing"; }
    @Override public String getName() { return "心晴"; }
    @Override public String getDuty() { return "心理支持"; }

    @Override
    public String getSystemPrompt() {
        return promptService.getPrompt(getId());
    }

    @Override
    public List<String> getKeywords() {
        return List.of("心理", "情绪", "失眠", "焦虑", "抑郁", "谈心", "疏导");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"心晴"的身份，为高校辅导员处理以下心理支持需求。
                请严格遵循安全边界，输出专业、温和、可操作的方案或话术。

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
