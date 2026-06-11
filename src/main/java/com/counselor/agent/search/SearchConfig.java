package com.counselor.agent.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Search configuration loaded from application.yml under {@code counselor.search}.
 */
@ConfigurationProperties(prefix = "counselor.search")
public record SearchConfig(
    boolean enabled,
    String provider,
    Zhipu zhipu,
    int maxResults,
    int maxExtractUrls,
    int maxIterations,
    int llmMaxConcurrency,
    int timeoutSeconds
) {
    public SearchConfig {
        if (maxResults <= 0) maxResults = 5;
        if (maxExtractUrls <= 0) maxExtractUrls = 10;
        if (maxIterations <= 0) maxIterations = 3;
        if (llmMaxConcurrency <= 0) llmMaxConcurrency = 2;
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
        if (zhipu == null) zhipu = new Zhipu("");
    }

    /** Zhipu MCP specific config. */
    public record Zhipu(String apiKey) {}
}
