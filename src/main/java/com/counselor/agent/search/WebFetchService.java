package com.counselor.agent.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Fetch and clean web page content.
 *
 * Two-route strategy (mirrors AI-Debate's fetch_page_content):
 * Route 1: Direct Jsoup fetch + HTML cleaning (good for SSR pages)
 * Route 2: Zhipu MCP Web Reader (JS rendering, good for SPA pages) — optional, requires API key
 *
 * Returns cleaned text up to 5000 characters, or empty string on failure.
 */
@Service
public class WebFetchService {

    private static final Logger log = LoggerFactory.getLogger(WebFetchService.class);
    private static final String MCP_READER_URL = "https://open.bigmodel.cn/api/mcp/web_reader/mcp";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_CHARS = 5000;

    private final SearchConfig config;

    public WebFetchService(SearchConfig config) {
        this.config = config;
    }

    /**
     * Fetch and clean page content from a URL.
     *
     * @param url the page URL to fetch
     * @return cleaned text (≤5000 chars), or empty string on total failure
     */
    public String fetchPage(String url) {
        // Route 1: Direct Jsoup fetch (fast, no JS rendering)
        try {
            String content = fetchViaJsoup(url);
            if (content != null && content.length() >= 200) {
                log.debug("Jsoup fetch OK: {} ({} chars)", url.substring(0, Math.min(60, url.length())), content.length());
                return clip(content, MAX_CHARS);
            }
            log.debug("Jsoup fetch too short for {} ({} chars), trying MCP reader",
                url.substring(0, Math.min(60, url.length())), content == null ? 0 : content.length());
        } catch (Exception e) {
            log.debug("Jsoup fetch failed for {}: {}", url.substring(0, Math.min(60, url.length())), e.getMessage());
        }

        // Route 2: Zhipu MCP Web Reader (JS rendering), only if API key is configured
        String apiKey = config.zhipu().apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                String content = fetchViaMcpReader(url, apiKey);
                if (content != null && !content.isBlank()) {
                    log.debug("MCP Reader OK: {} ({} chars)", url.substring(0, Math.min(60, url.length())), content.length());
                    return clip(content, MAX_CHARS);
                }
            } catch (Exception e) {
                log.debug("MCP Reader failed for {}: {}", url.substring(0, Math.min(60, url.length())), e.getMessage());
            }
        }

        return "";
    }

    // ── Route 1: Jsoup ──

    private String fetchViaJsoup(String url) {
        try {
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .timeout(config.timeoutSeconds() * 1000)
                .followRedirects(true)
                .get();

            // Remove noise elements
            doc.select("script, style, nav, header, footer, aside, noscript").remove();

            // Find main content area (heuristic priority)
            var main = doc.selectFirst("article");
            if (main == null) main = doc.selectFirst("main");
            if (main == null) main = doc.selectFirst("[class*=content], [class*=article], [class*=body]");
            if (main == null) main = doc.selectFirst("body");
            if (main == null) main = doc;

            String text = main.text();
            // Clean up whitespace
            text = text.replaceAll("\\s+", " ").trim();

            // SPA detection
            if (text.length() < 500) {
                log.debug("Jsoup: short content ({} chars) — likely SPA/JS-rendered page: {}",
                    text.length(), url.substring(0, Math.min(60, url.length())));
            }

            return text;
        } catch (Exception e) {
            log.debug("Jsoup fetch error for {}: {}", url.substring(0, Math.min(60, url.length())), e.getMessage());
            return "";
        }
    }

    // ── Route 2: Zhipu MCP Web Reader ──

    private String fetchViaMcpReader(String url, String apiKey) {
        var client = RestClient.builder()
            .defaultHeaders(h -> {
                h.setBearerAuth(apiKey);
                h.setContentType(MediaType.APPLICATION_JSON);
                h.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream")));
            })
            .build();

        try {
            // 1. Initialize
            var initResp = client.post()
                .uri(MCP_READER_URL)
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

            String sid = initResp.getHeaders().getFirst("mcp-session-id");

            // 2. Notify initialized
            var notifyBuilder = client.post().uri(MCP_READER_URL);
            if (sid != null) notifyBuilder.header("Mcp-Session-Id", sid);
            notifyBuilder.body(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"))
                .retrieve().toBodilessEntity();

            // 3. Call webReader
            var readBuilder = client.post().uri(MCP_READER_URL);
            if (sid != null) readBuilder.header("Mcp-Session-Id", sid);
            String readResp = readBuilder.body(Map.of(
                "jsonrpc", "2.0",
                "method", "tools/call",
                "params", Map.of(
                    "name", "webReader",
                    "arguments", Map.of("url", url)
                ),
                "id", 2
            )).retrieve().body(String.class);

            JsonNode result = ZhipuMCPSearchProvider.parseSseJson(readResp);
            if (result == null) return "";

            JsonNode contentList = result.path("result").path("content");
            if (contentList.isArray()) {
                for (JsonNode item : contentList) {
                    if ("text".equals(item.path("type").asText())) {
                        JsonNode textVal = item.path("text");
                        return parseReaderResponse(textVal);
                    }
                }
            }

        } catch (Exception e) {
            log.debug("MCP Reader error: {}", e.getMessage());
        }

        return "";
    }

    /**
     * Parse MCP web_reader response, which may be:
     * - A JSON object {title, content, url}
     * - A plain string
     * - A double-encoded JSON string
     */
    private String parseReaderResponse(JsonNode textVal) {
        if (textVal.isObject()) {
            String content = textVal.path("content").asText("");
            if (!content.isBlank()) return content;
        }
        if (textVal.isTextual()) {
            String raw = textVal.asText();
            // Try parsing as JSON object
            try {
                JsonNode parsed = mapper.readTree(raw);
                if (parsed.isObject()) {
                    String content = parsed.path("content").asText("");
                    if (!content.isBlank()) {
                        String title = parsed.path("title").asText("");
                        if (!title.isBlank()) {
                            content = "# " + title + "\n\n" + content;
                        }
                        return content;
                    }
                }
                if (parsed.isTextual()) return parsed.asText();
                if (parsed.isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode p : parsed) {
                        if (p.isTextual()) sb.append(p.asText()).append("\n");
                    }
                    return sb.toString();
                }
            } catch (Exception e) {
                // Not JSON, return as plain text
                return raw;
            }
        }
        return "";
    }

    private static String clip(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }
}
