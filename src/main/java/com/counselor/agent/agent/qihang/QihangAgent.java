package com.counselor.agent.agent.qihang;

import com.counselor.agent.agent.SubAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class QihangAgent implements SubAgent {

    private final ChatClient chatClient;

    public QihangAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override public String getId() { return "qihang"; }
    @Override public String getName() { return "启航"; }
    @Override public String getDuty() { return "就业创业"; }

    @Override
    public String getSystemPrompt() {
        return """
                你是"启航"，就业创业指导助手。分析学生专业背景推荐就业方向、整理招聘信息、提供简历修改建议、讲解创业政策。
                输出应具体可操作，包含：岗位/行业推荐及理由、简历优化建议、面试准备要点、相关政策文件号。
                注意：就业市场信息变化快，涉及具体薪资、招聘截止日期等数据时，建议老师核实最新信息。
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("就业", "求职", "简历", "面试", "创业", "招聘", "职业规划");
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
