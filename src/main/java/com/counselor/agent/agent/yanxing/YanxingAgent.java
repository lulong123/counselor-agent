package com.counselor.agent.agent.yanxing;

import com.counselor.agent.agent.SubAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class YanxingAgent implements SubAgent {

    private final ChatClient chatClient;

    public YanxingAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override public String getId() { return "yanxing"; }
    @Override public String getName() { return "研行"; }
    @Override public String getDuty() { return "实践研究"; }

    @Override
    public String getSystemPrompt() {
        return """
                你是"研行"，实践研究助手。设计调研问卷、撰写课题申报书框架、分析实践数据、撰写辅导员工作案例。
                输出包含：方法论说明、样本量建议、数据分析方法、成果呈现方式。
                格式符合学术规范，适合直接用于课题申报或工作案例评选。
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("调研", "课题", "问卷", "论文", "案例", "实践", "研究");
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
