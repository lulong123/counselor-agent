package com.counselor.agent.agent;

import reactor.core.publisher.Flux;

import java.util.List;

public interface SubAgent {

    String getId();

    String getName();

    String getDuty();

    String getSystemPrompt();

    /** 路由关键词列表，供 ChiefAgent 作为 LLM 分类的参考依据 */
    List<String> getKeywords();

    /** 扩展口子：工具列表，当前返回空，后续加 WebSearch 等无需改接口 */
    default List<Object> getTools() {
        return List.of();
    }

    /** 拼接 System Prompt → 调 LLM → 流式返回 */
    Flux<String> execute(String userInput);
}
