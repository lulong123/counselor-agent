package com.counselor.agent.search;

import java.util.List;

/**
 * No-op search provider returned when search is disabled or unconfigured.
 * Always returns an empty result list.
 */
public class NoOpSearchProvider implements SearchProvider {

    @Override
    public List<SearchResult> search(String query, int maxResults, String recency) {
        return List.of();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
