package com.counselor.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Intercepts HTTP responses from DeepSeek API and extracts {@code reasoning_content}
 * (the model's chain-of-thought).
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li><b>Non-streaming</b> — buffers the full response, extracts reasoning_content,
 *       stores it in a ThreadLocal for RunService to pick up.</li>
 *   <li><b>Streaming</b> — wraps the response InputStream with a {@link ReasoningFilterInputStream}
 *       that intercepts each SSE chunk as it arrives and forwards reasoning_content
 *       directly to the frontend via SseEmitter in real time.</li>
 * </ul>
 */
public class ReasoningCaptureInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ReasoningCaptureInterceptor.class);

    private static final ThreadLocal<String> lastReasoning = new ThreadLocal<>();
    private static volatile SseEmitter activeEmitter;

    // ── Emitter management (called by RunService) ──

    /** Register the emitter — reasoning will stream to it in real time. */
    public static void setEmitter(SseEmitter emitter) {
        activeEmitter = emitter;
        log.info("Emitter registered");
    }

    /** Remove the emitter registration. */
    public static void clearEmitter() {
        activeEmitter = null;
        log.info("Emitter cleared");
    }

    /** Get the last captured reasoning content (non-streaming mode) and clear the slot. */
    @Nullable
    public static String getAndClear() {
        String r = lastReasoning.get();
        lastReasoning.remove();
        return r;
    }

    // ── Intercept ──

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                         ClientHttpRequestExecution execution) throws IOException {
        String uri = request.getURI().toString();
        if (!uri.contains("deepseek") || !uri.contains("chat/completions")) {
            return execution.execute(request, body);
        }

        // If an emitter is registered, force stream=true so DeepSeek sends SSE chunks.
        // Spring AI 1.0.0-M6 does NOT set stream=true even with .stream().chatResponse().
        SseEmitter emitter = activeEmitter; // volatile read — not ThreadLocal
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        if (emitter != null && !bodyStr.contains("\"stream\":true") && !bodyStr.contains("\"stream\": true")) {
            bodyStr = bodyStr.replaceFirst("\\}$", ",\"stream\":true}");
            body = bodyStr.getBytes(StandardCharsets.UTF_8);
            log.info("Forced stream=true in request (emitter registered), bodyLen={}", body.length);
        }

        ClientHttpResponse response = execution.execute(request, body);

        if (response.getStatusCode().isError()) {
            return response;
        }

        boolean isStream = bodyStr.contains("\"stream\":true") || bodyStr.contains("\"stream\": true");
        log.info("DeepSeek request — stream={}, bodyLen={}", isStream, body.length);

        if (isStream && emitter != null) {
            log.info("Wrapping response InputStream for real-time reasoning capture");
            return wrapStreaming(response, emitter);
        }

        // ── Non-streaming: buffer the full response ──
        byte[] raw = response.getBody().readAllBytes();
        extractAndStore(new String(raw, StandardCharsets.UTF_8));

        byte[] bufferedBody = raw;
        HttpStatusCode statusCode = response.getStatusCode();
        String statusText = response.getStatusText();
        var headers = response.getHeaders();

        return new ClientHttpResponse() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(bufferedBody); }
            @Override public HttpStatusCode getStatusCode() { return statusCode; }
            @Override public String getStatusText() { return statusText; }
            @Override public void close() { response.close(); }
            @Override public org.springframework.http.HttpHeaders getHeaders() { return headers; }
        };
    }

    private ClientHttpResponse wrapStreaming(ClientHttpResponse response, SseEmitter emitter) throws IOException {
        InputStream wrappedStream = new ReasoningFilterInputStream(response.getBody(), emitter);
        HttpStatusCode statusCode = response.getStatusCode();
        String statusText = response.getStatusText();
        var headers = response.getHeaders();

        return new ClientHttpResponse() {
            @Override public InputStream getBody() { return wrappedStream; }
            @Override public HttpStatusCode getStatusCode() { return statusCode; }
            @Override public String getStatusText() { return statusText; }
            @Override public void close() { response.close(); }
            @Override public org.springframework.http.HttpHeaders getHeaders() { return headers; }
        };
    }

    // ── Non-streaming extraction (unchanged) ──

    private void extractAndStore(String bodyStr) {
        try {
            StringBuilder reasoning = new StringBuilder();
            int pos = 0;
            while ((pos = bodyStr.indexOf("\"reasoning_content\"", pos)) >= 0) {
                int colon = bodyStr.indexOf(':', pos);
                if (colon < 0) break;

                int openQuote = -1;
                for (int i = colon + 1; i < bodyStr.length(); i++) {
                    char c = bodyStr.charAt(i);
                    if (c == '"') { openQuote = i; break; }
                    if (c != ' ' && c != '\n' && c != '\t') break;
                }
                if (openQuote < 0) { pos = colon + 1; continue; }

                int closeQuote = -1;
                for (int i = openQuote + 1; i < bodyStr.length(); i++) {
                    char c = bodyStr.charAt(i);
                    if (c == '\\') { i++; continue; }
                    if (c == '"') { closeQuote = i; break; }
                }
                if (closeQuote < 0) break;

                String raw = bodyStr.substring(openQuote + 1, closeQuote);
                String value = raw.replace("\\\"", "\"")
                           .replace("\\n", "\n")
                           .replace("\\t", "\t")
                           .replace("\\r", "\r")
                           .replace("\\\\", "\\");
                if (!value.isBlank()) {
                    reasoning.append(value);
                }
                pos = closeQuote + 1;
            }

            if (!reasoning.isEmpty()) {
                lastReasoning.set(reasoning.toString());
                log.debug("Captured reasoning_content ({} chars)", reasoning.length());
            }
        } catch (Exception e) {
            log.debug("Failed to extract reasoning_content: {}", e.getMessage());
        }
    }

    // ── Streaming InputStream wrapper ──

    /**
     * Wraps the raw HTTP response InputStream from DeepSeek's SSE stream.
     * As bytes arrive, it parses SSE lines, extracts {@code reasoning_content},
     * and pushes it to the frontend via the registered {@link SseEmitter}
     * — in real time, before Spring AI even finishes processing the chunk.
     */
    static class ReasoningFilterInputStream extends FilterInputStream {

        private final SseEmitter emitter;
        private final ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();

        ReasoningFilterInputStream(InputStream in, SseEmitter emitter) {
            super(in);
            this.emitter = emitter;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b == -1) return -1;
            if (b == '\n') {
                processLine(lineBuf.toString(StandardCharsets.UTF_8));
                lineBuf.reset();
            } else {
                lineBuf.write(b);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0) {
                for (int i = off; i < off + n; i++) {
                    byte by = b[i];
                    if (by == '\n') {
                        processLine(lineBuf.toString(StandardCharsets.UTF_8));
                        lineBuf.reset();
                    } else {
                        lineBuf.write(by);
                    }
                }
            }
            return n;
        }

        @Override
        public void close() throws IOException {
            if (lineBuf.size() > 0) {
                processLine(lineBuf.toString(StandardCharsets.UTF_8));
            }
            in.close();
        }

        private void processLine(String line) {
            if (!line.startsWith("data:")) return;
            String json = line.substring(5).trim();
            if (json.isEmpty() || json.equals("[DONE]")) return;

            String reasoning = extractReasoningFromChunk(json);
            if (reasoning != null && !reasoning.isBlank()) {
                log.info("Reasoning chunk: {} chars", reasoning.length());
                try {
                    emitter.send(SseEmitter.event()
                            .name("agent_thinking")
                            .data(Map.of("content", reasoning)));
                } catch (IOException e) {
                    log.debug("Failed to send reasoning chunk: {}", e.getMessage());
                }
            }
        }

        /** Extract reasoning_content value from a single SSE chunk JSON. */
        private static String extractReasoningFromChunk(String json) {
            int idx = json.indexOf("\"reasoning_content\"");
            if (idx < 0) return null;

            int colon = json.indexOf(':', idx);
            if (colon < 0) return null;

            int openQuote = -1;
            for (int i = colon + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '"') { openQuote = i; break; }
                if (c != ' ' && c != '\n' && c != '\t') break;
            }
            if (openQuote < 0) return null;

            int closeQuote = -1;
            for (int i = openQuote + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\') { i++; continue; }
                if (c == '"') { closeQuote = i; break; }
            }
            if (closeQuote < 0) return null;

            String raw = json.substring(openQuote + 1, closeQuote);
            return raw.replace("\\\"", "\"")
                      .replace("\\n", "\n")
                      .replace("\\t", "\t")
                      .replace("\\r", "\r")
                      .replace("\\\\", "\\");
        }
    }
}
