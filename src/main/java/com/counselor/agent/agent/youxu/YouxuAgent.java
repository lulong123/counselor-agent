package com.counselor.agent.agent.youxu;

import com.counselor.agent.agent.SubAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class YouxuAgent implements SubAgent {

    private final ChatClient chatClient;

    public YouxuAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override public String getId() { return "youxu"; }
    @Override public String getName() { return "有序"; }
    @Override public String getDuty() { return "日常事务"; }

    @Override
    public String getSystemPrompt() {
        return """
                你是"有序"，日常事务管理助手。处理材料收发、通知公告起草、评奖评优流程。
                输出包含：明确的时间节点、操作步骤、注意事项、常见问题解答。
                格式清晰、层次分明，老师可直接转发到班级群或作为工作备忘。
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("材料", "收集", "通知", "评奖", "评优", "奖学金", "助学金", "表格");
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
