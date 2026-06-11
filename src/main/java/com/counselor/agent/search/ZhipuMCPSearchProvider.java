package com.counselor.agent.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Search via Zhipu MCP web_search_prime protocol (JSON-RPC 2.0 over HTTP).
 *
 * Three-step handshake:
 * 1. initialize  → get mcp-session-id
 * 2. notifications/initialized → confirm
 * 3. tools/call (name="web_search_prime") → actual search
 *
 * Direct Java translation of AI-Debate's ZhipuMCPSearchProvider.
 */
public class ZhipuMCPSearchProvider implements SearchProvider {

    private static final Logger log = LoggerFactory.getLogger(ZhipuMCPSearchProvider.class);
    private static final String MCP_SEARCH_URL = "https://open.bigmodel.cn/api/mcp/web_search_prime/mcp";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final RestClient client;

    public ZhipuMCPSearchProvider(String apiKey) {
        this.apiKey = apiKey;
        this.client = RestClient.builder()
            .defaultHeaders(h -> {
                h.setBearerAuth(apiKey);
                h.setContentType(MediaType.APPLICATION_JSON);
                h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream")));
            })
            .build();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults, String recency) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("ZhipuMCP: no API key configured, returning empty");
            return List.of();
        }

        try {
            // Step 1: Initialize MCP session
            String sessionId = initialize();
            if (sessionId == null || sessionId.isBlank()) {
                log.warn("ZhipuMCP: failed to initialize MCP session");
                return List.of();
            }

            // Step 2: Notify initialized
            notifyInitialized(sessionId);

            // Step 3: Call web_search_prime tool
            var arguments = new java.util.LinkedHashMap<String, Object>();
            arguments.put("search_query", query);
            arguments.put("location", "cn");
            arguments.put("content_size", "high");
            if (!"noLimit".equals(recency)) {
                arguments.put("search_recency_filter", recency);
            }

            String searchResp = client.post()
                .uri(MCP_SEARCH_URL)
                .header("Mcp-Session-Id", sessionId)
                .body(Map.of(
                    "jsonrpc", "2.0",
                    "method", "tools/call",
                    "params", Map.of(
                        "name", "web_search_prime",
                        "arguments", arguments
                    ),
                    "id", 2
                ))
                .retrieve()
                .body(String.class);

            JsonNode result = parseSseJson(searchResp);
            if (result == null) return List.of();

            JsonNode contentList = result.path("result").path("content");
            if (contentList.isArray()) {
                for (JsonNode item : contentList) {
                    if ("text".equals(item.path("type").asText())) {
                        String text = item.path("text").asText();
                        return extractResults(text, maxResults);
                    }
                }
            }

            log.warn("ZhipuMCP: unexpected response structure: {}",
                searchResp.length() > 300 ? searchResp.substring(0, 300) : searchResp);
            return List.of();

        } catch (Exception e) {
            log.warn("ZhipuMCP search error for query '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    // ── MCP protocol helpers ──

    private String initialize() {
        var resp = client.post()
            .uri(MCP_SEARCH_URL)
            .body(Map.of(
                "jsonrpc", "2.0",
                "method", "initialize",
                "params", Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "counselor-agent", "version", "1.0.0")
                ),
                "id", 1
            ))
            .retrieve()
            .toEntity(String.class);

        // MCP session ID comes from response header
        String sid = resp.getHeaders().getFirst("mcp-session-id");
        if (sid != null && !sid.isBlank()) {
            log.debug("ZhipuMCP: session initialized, id={}", sid.substring(0, Math.min(8, sid.length())));
        }
        return sid;
    }

    private void notifyInitialized(String sessionId) {
        try {
            client.post()
                .uri(MCP_SEARCH_URL)
                .header("Mcp-Session-Id", sessionId)
                .body(Map.of(
                    "jsonrpc", "2.0",
                    "method", "notifications/initialized"
                ))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.debug("ZhipuMCP: initialized notification failed (non-fatal): {}", e.getMessage());
        }
    }

    // ── Response parsing ──

    /**
     * Parse SSE-format response: {@code data:{json}\\n\\n}
     */
    static JsonNode parseSseJson(String raw) {
        if (raw == null) return null;
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                String data = trimmed.substring(5).trim();
                if (!data.isEmpty()) {
                    try {
                        return mapper.readTree(data);
                    } catch (Exception e) {
                        log.debug("ZhipuMCP: failed to parse SSE data line: {}", e.getMessage());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Parse MCP tool result text into SearchResult list.
     * The result text may be a JSON string containing an array of search items.
     */
    static List<SearchResult> extractResults(String rawText, int maxResults) {
        if (rawText == null || rawText.isBlank()) return List.of();
        if (rawText.startsWith("MCP error")) {
            log.warn("ZhipuMCP: search blocked — {}", rawText);
            return List.of();
        }

        List<SearchResult> results = new ArrayList<>();
        try {
            // Try parsing as JSON — the result is a JSON string that may contain an array
            JsonNode inner = mapper.readTree(rawText);

            // Handle double-encoded JSON string
            JsonNode items;
            if (inner.isTextual()) {
                items = mapper.readTree(inner.asText());
            } else {
                items = inner;
            }

            if (items.isArray()) {
                for (JsonNode item : items) {
                    results.add(new SearchResult(
                        item.path("title").asText(""),
                        item.path("content").asText(""),
                        item.path("link").asText(""),
                        item.path("date").asText("")
                    ));
                    if (results.size() >= maxResults) break;
                }
            }
        } catch (Exception e) {
            log.debug("ZhipuMCP: failed to parse result JSON: {}", e.getMessage());
        }

        return results;
    }

    @Override
    public void close() {
        // RestClient is stateless, no cleanup needed
    }
}
