package com.counselor.agent.agent.yanxing;

import com.counselor.agent.agent.SubAgent;
import com.counselor.agent.agent.AgentPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class YanxingAgent implements SubAgent {

    private final ChatClient chatClient;
    private final AgentPromptService promptService;

    public YanxingAgent(ChatClient chatClient, AgentPromptService promptService) {
        this.chatClient = chatClient;
        this.promptService = promptService;
    }

    @Override public String getId() { return "yanxing"; }
    @Override public String getName() { return "研行"; }
    @Override public String getDuty() { return "实践研究"; }

    @Override
    public String getSystemPrompt() {
        return promptService.getPrompt(getId());
    }

    @Override
    public List<String> getKeywords() {
        return List.of("调研", "课题", "问卷", "论文", "案例", "实践", "研究");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"研行"的身份，为高校辅导员处理以下实践研究需求。
                请严格按照系统提示词中的结构要求输出，确保方法论扎实、符合学术规范。

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
