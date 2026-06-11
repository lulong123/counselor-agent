package com.counselor.agent.agent.youxu;

import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.AgentPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class YouxuAgent implements SubAgent {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;

    public YouxuAgent(ChatClient chatClient, AgentPromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    @Override public String getId() { return "youxu"; }
    @Override public String getName() { return "有序"; }
    @Override public String getDuty() { return "日常事务"; }

    @Override
    public String getSystemPrompt() {
        return promptService.getPrompt(getId());
    }

    @Override
    public List<String> getKeywords() {
        return List.of("材料", "收集", "通知", "评奖", "评优", "奖学金", "助学金", "表格");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"有序"的身份，为高校辅导员处理以下日常事务需求。
                请严格按照系统提示词中的结构要求输出，确保信息完整、可直接使用。

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
