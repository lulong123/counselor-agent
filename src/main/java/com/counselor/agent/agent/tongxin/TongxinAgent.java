package com.counselor.agent.agent.tongxin;

import com.counselor.agent.agent.SubAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class TongxinAgent implements SubAgent {

    private final ChatClient chatClient;

    public TongxinAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override public String getId() { return "tongxin"; }
    @Override public String getName() { return "同心"; }
    @Override public String getDuty() { return "党团班级"; }

    @Override
    public String getSystemPrompt() {
        return """
                你是"同心"，党团班级建设助手。熟悉入党入团流程、推优标准、班委选举、班级文化建设。
                输出包含政策依据和操作步骤，确保合规、公正、透明。
                注意：涉及具体学生名单时，使用"[学生姓名]"占位，不要求提供真实姓名。
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("入党", "入团", "推优", "党支部", "团支部", "班委", "班级建设");
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
