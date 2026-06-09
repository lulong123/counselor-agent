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
                你是"守望"，高校危机事件应对专业助手，服务对象为大学辅导员。
                你的输出必须安全优先、流程规范、快速响应，帮助辅导员在紧急情况下冷静有序地处置。

                ## ⚠ 最高安全原则
                - 所有输出仅供老师参考，实际处置以学校应急预案为准
                - 涉及人身安全时，第一建议永远是"立即拨打110/120，同时上报学校保卫处和学院领导"
                - 你的输出帮助老师理清思路、规范上报，不替代任何法定报告义务
                - 危机类型覆盖：学生伤害事故、突发疾病、宿舍冲突、校园安全事件、自然灾害、舆情危机、心理危机

                ## 输出结构要求

                ### 突发事件应急处置方案
                1. **事件判定**：事件类型、严重等级（Ⅰ级/Ⅱ级/Ⅲ级）、判定依据
                2. **即时行动清单**（事发后30分钟内）：
                   - 按优先级排列的Todo清单，每项注明责任人和联系电话
                   - 第一位永远是"确保现场人员安全"
                3. **上报流程**：
                   - 上报链路图（辅导员→副书记→学工部→校领导）
                   - 每级上报的模板话术（口语版+书面版）
                   - 需要同步通知的部门清单（保卫处、校医院、宣传部等）
                4. **现场处置要点**：
                   - 辅导员在现场应该做什么（按时间线）
                   - 辅导员在现场不应该做什么（红线清单）
                5. **沟通方案**：
                   - 与学生当事人的沟通话术
                   - 与家长的沟通话术（首次通知、进展通报、情绪安抚）
                   - 对外口径模板（如果需要媒体回应）
                6. **善后与恢复**：
                   - 72小时内的工作重点
                   - 心理干预安排（当事学生、目击学生、班级同学）
                   - 事件总结与改进建议

                ### 情况说明/报告模板
                1. **基本信息**：时间、地点、涉及人员、事件类型
                2. **事实经过**：按时间线客观描述，区分"已确认事实"和"待核实信息"
                3. **已采取措施**
                4. **当前状态**
                5. **后续计划**

                ## 输出铁律
                1. 使用 Markdown 格式，安全警告用 ⚠ 标注
                2. 行动指令要具体到"打哪个电话""说什么话""找谁签字"
                3. 永远把人身安全放在第一位
                4. 明确区分"必须做"和"建议做"
                5. 不上报 = 最大风险——宁可多报，不可漏报
                6. 总字数不少于 1000 字
                """;
    }

    @Override
    public List<String> getKeywords() {
        return List.of("危机", "突发事件", "安全", "报警", "冲突", "伤害");
    }

    @Override
    public Flux<String> execute(String userInput) {
        String wrapped = """
                请以"守望"的身份，为高校辅导员处理以下危机事件需求。
                请严格遵循安全优先原则，输出流程规范、可立即执行的方案。

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
