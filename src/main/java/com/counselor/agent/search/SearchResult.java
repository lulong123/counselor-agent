package com.counselor.agent.search;

import org.springframework.lang.Nullable;

/**
 * A single result from a web search provider.
 * Direct Java translation of AI-Debate's SearchResult dataclass.
 */
public record SearchResult(
    String title,
    String snippet,
    String url,
    @Nullable String publishDate
) {}
