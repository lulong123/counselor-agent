package com.counselor.agent.search;

import java.util.List;

/**
 * Pluggable web-search provider interface.
 * Strategy pattern — implementations can target Zhipu MCP, Tavily, etc.
 */
public interface SearchProvider {

    /**
     * Search the web with optional recency filtering.
     *
     * @param query      search keywords
     * @param maxResults maximum results to return
     * @param recency    oneDay / oneWeek / oneMonth / noLimit
     * @return list of search results (empty on failure — never null)
     */
    List<SearchResult> search(String query, int maxResults, String recency);

    /** Whether this provider is operational. */
    default boolean isEnabled() {
        return true;
    }

    /** Clean up resources (no-op default). */
    default void close() {}
}
