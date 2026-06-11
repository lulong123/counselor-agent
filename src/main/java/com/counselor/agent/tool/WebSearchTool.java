package com.counselor.agent.tool;

import com.counselor.agent.search.SearchConfig;
import com.counselor.agent.search.SearchProvider;
import com.counselor.agent.search.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WebSearchTool implements FunctionCallback {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final SearchProvider searchProvider;
    private final SearchConfig config;

    public WebSearchTool(SearchProvider searchProvider, SearchConfig config) {
        this.searchProvider = searchProvider;
        this.config = config;
    }

    @Override public String getName() { return "web_search"; }

    @Override
    public String getDescription() {
        return "Search the web for current information, news, articles, and facts. "
                + "Use this tool when you need real-time or up-to-date information "
                + "that may not be in your training data. "
                + "For time-sensitive queries (prices, news, events), set recency to 'oneDay'. "
                + "Be specific in your query for better results.";
    }

    @Override
    public String getInputTypeSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "Search keywords. Be specific and include key terms. For time-sensitive info, include date/time in the query."
                    },
                    "max_results": {
                      "type": "integer",
                      "description": "Maximum number of results (default: 5, max: 10)"
                    },
                    "recency": {
                      "type": "string",
                      "description": "Time filter: oneDay (24h), oneWeek (7d), oneMonth (30d), or noLimit (all). Use oneDay for breaking news, prices, weather."
                    }
                  },
                  "required": ["query"]
                }""";
    }

    @Override
    public String call(String functionInput) {
        if (!searchProvider.isEnabled()) {
            return "{\"error\": \"Search is not available\", \"results\": []}";
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = mapper.readValue(functionInput, Map.class);
            String query = (String) args.getOrDefault("query", functionInput);
            int maxResults = args.get("max_results") instanceof Number n
                    ? n.intValue() : config.maxResults();
            String recency = args.get("recency") instanceof String s ? s : "noLimit";

            log.info("web_search: query='{}', maxResults={}, recency={}", query, maxResults, recency);
            List<SearchResult> results = searchProvider.search(query, maxResults, recency);

            StringBuilder sb = new StringBuilder();
            sb.append("{\"query\":\"").append(escape(query)).append("\",");
            sb.append("\"total_results\":").append(results.size()).append(",");
            sb.append("\"results\":[");
            for (int i = 0; i < results.size(); i++) {
                if (i > 0) sb.append(",");
                SearchResult r = results.get(i);
                sb.append("{\"title\":\"").append(escape(r.title())).append("\",");
                sb.append("\"url\":\"").append(escape(r.url())).append("\",");
                sb.append("\"content\":\"").append(escape(r.snippet())).append("\"}");
            }
            sb.append("]}");
            return sb.toString();

        } catch (Exception e) {
            log.warn("web_search failed: {}", e.getMessage());
            return "{\"error\": \"" + escape(e.getMessage()) + "\", \"results\": []}";
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
