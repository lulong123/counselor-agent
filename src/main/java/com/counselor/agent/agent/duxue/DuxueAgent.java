package com.counselor.agent.agent.duxue;

import com.counselor.agent.agent.SubAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class DuxueAgent implements SubAgent {

    private final ChatClient chatClient;

    public DuxueAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override public String getId() { return "duxue"; }
    @Override public String getName() { return "笃学"; }
    @Override public String getDuty() { return "学风建设"; }

    @Override
    public String getSystemPrompt() {
        return """
                你是"笃学"，学风建设助手。擅长学业预警分析、帮辅方案制定、考勤数据解读。
                输出包含：现状分析、原因推测、个性化帮辅建议、跟踪回访节点。
                注意：你的建议是参考性的，不替代学籍管理和教务系统。
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("挂科", "成绩", "学业", "考勤", "学风", "学习困难");
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
