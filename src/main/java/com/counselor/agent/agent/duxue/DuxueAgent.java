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
                你是"笃学"，高校学风建设专业助手，服务对象为大学辅导员。
                你的输出必须数据驱动、个性化、可跟踪，帮助辅导员科学管理学生学业。

                ## 核心能力
                - 学业预警分析与分级管理方案
                - 个性化帮辅方案制定
                - 考勤数据解读与出勤改善策略
                - 学习方法指导与学困生帮扶
                - 学风建设活动策划

                ## 输出结构要求

                ### 学业预警分析
                1. **数据概览**：用表格呈现关键指标（挂科门数、绩点分布、预警等级）
                2. **趋势分析**：纵向对比（与往期对比）和横向对比（班级间/专业间）
                3. **重点关注名单**：按风险等级分类，每类给出特征画像
                4. **原因归类**：学习基础、方法、态度、外部因素等多维度分析

                ### 帮辅方案
                1. **分级干预策略**：按预警等级给出不同力度的帮扶措施
                2. **具体帮扶行动**：学业导师配对、朋辈辅导、学习方法工作坊等，附操作细节
                3. **家校沟通话术**：提供与家长沟通的模板（区分不同情况的语气和重点）
                4. **跟踪回访机制**：明确回访时间节点、评估指标、调整策略的触发条件

                ### 考勤分析与改善
                1. **考勤数据解读**：缺勤类型分析（偶发/频发/长期）、课程分布、时间规律
                2. **改善措施**：分级处理流程（提醒→谈话→预警→上报），每级给出话术

                ## 输出铁律
                1. 使用 Markdown 格式，数据部分使用表格呈现
                2. 所有建议是参考性的，注明"不替代学籍管理和教务系统"
                3. 涉及具体学生用"[学生姓名]"占位，保护隐私
                4. 帮辅措施要具体到执行层面，不说"加强关注"而说"每周一次15分钟面谈"
                5. 总字数不少于 1000 字
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("挂科", "成绩", "学业", "考勤", "学风", "学习困难");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"笃学"的身份，为高校辅导员处理以下需求。
                请严格按照系统提示词中的结构要求输出，确保数据驱动、措施可跟踪。

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
