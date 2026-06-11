package com.counselor.agent.tool;

import com.counselor.agent.search.WebFetchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WebFetchTool implements FunctionCallback {

    private static final Logger log = LoggerFactory.getLogger(WebFetchTool.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_CHARS = 4000;

    private final WebFetchService webFetchService;

    public WebFetchTool(WebFetchService webFetchService) {
        this.webFetchService = webFetchService;
    }

    @Override public String getName() { return "web_fetch"; }

    @Override
    public String getDescription() {
        return "Fetch the full content of a web page at a given URL. "
                + "Only use this on URLs returned by web_search. "
                + "Returns cleaned text content (up to " + MAX_CHARS + " characters). "
                + "URLs must include the schema (https://example.com).";
    }

    @Override
    public String getInputTypeSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "url": {
                      "type": "string",
                      "description": "The full URL to fetch, including https://"
                    }
                  },
                  "required": ["url"]
                }""";
    }

    @Override
    public String call(String functionInput) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = mapper.readValue(functionInput, Map.class);
            String url = (String) args.get("url");
            if (url == null || url.isBlank()) {
                return "Error: no URL provided";
            }

            log.info("web_fetch: url={}", url.length() > 80 ? url.substring(0, 80) + "..." : url);
            String content = webFetchService.fetchPage(url);

            if (content.isEmpty()) {
                return "Error: could not fetch content from " + url;
            }

            String truncated = content.length() > MAX_CHARS
                    ? content.substring(0, MAX_CHARS) + "\n\n[content truncated at " + MAX_CHARS + " chars]"
                    : content;

            return "# Fetched page\n\n" + truncated;

        } catch (Exception e) {
            log.warn("web_fetch failed: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
