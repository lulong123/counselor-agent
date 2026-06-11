package com.counselor.agent.agent;

import org.springframework.ai.model.function.FunctionCallback;
import reactor.core.publisher.Flux;

import java.util.List;

public interface SubAgent {

    String getId();

    String getName();

    String getDuty();

    String getSystemPrompt();

    /** 路由关键词列表，供 ChiefAgent 作为 LLM 分类的参考依据 */
    List<String> getKeywords();

    /** 工具列表，返回此 Agent 可用的 FunctionCallback（如 web_search, web_fetch） */
    default List<FunctionCallback> getTools() {
        return List.of();
    }

    /** 拼接 System Prompt → 调 LLM → 流式返回 */
    Flux<String> execute(String userInput);
}
