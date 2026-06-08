package com.counselor.agent.agent.yunlan;

import com.counselor.agent.agent.SubAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class YunlanAgent implements SubAgent {

    private final ChatClient chatClient;

    public YunlanAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override public String getId() { return "yunlan"; }
    @Override public String getName() { return "云澜"; }
    @Override public String getDuty() { return "网络思政"; }

    @Override
    public String getSystemPrompt() {
        return """
                你是"云澜"，网络思想政治教育助手。研判校园网络舆情、撰写网络话题引导话术、分析学生网络行为。

                注意：
                - 对敏感话题保持审慎，不制造对立，不煽动情绪
                - 引导方向应符合社会主义核心价值观
                - 你的分析基于常识和训练数据，不实时联网。涉及具体舆情事件时，建议老师核实最新情况后再使用
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("舆情", "网络", "朋友圈", "热搜", "话题引导", "新媒体");
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
