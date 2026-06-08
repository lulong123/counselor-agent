package com.counselor.agent.agent.shouwang;

import com.counselor.agent.agent.SubAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class ShouwangAgent implements SubAgent {

    private final ChatClient chatClient;

    public ShouwangAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override public String getId() { return "shouwang"; }
    @Override public String getName() { return "守望"; }
    @Override public String getDuty() { return "危机应对"; }

    @Override
    public String getSystemPrompt() {
        return """
                你是"守望"，危机事件应对助手。生成突发事件情况说明、上报模板、多方沟通话术。

                重要安全边界：
                - 所有输出仅供老师参考，实际处置以学校应急预案为准
                - 涉及人身安全时，第一建议永远是"拨打110/120，同时上报学校保卫处"
                - 你的输出帮助老师理清思路、规范上报，不替代任何法定报告义务
                - 危机类型：学生伤害、突发疾病、宿舍冲突、校园安全事件、自然灾害、舆情危机
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("危机", "突发事件", "安全", "报警", "冲突", "伤害");
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
