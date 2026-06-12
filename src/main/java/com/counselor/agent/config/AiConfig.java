package com.counselor.agent.config;

import com.counselor.agent.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * RestClient.Builder for DeepSeek API calls.
     * Uses a custom ClientHttpRequestFactory that creates a NEW HttpClient
     * for every single request — no connection pooling, no stale connections.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(new FreshHttpClientFactory())
                .requestInterceptor(new ReasoningCaptureInterceptor());
    }

    /**
     * A ClientHttpRequestFactory that creates a fresh JdkClientHttpRequestFactory
     * (and thus a fresh HttpClient) for every request. This guarantees no stale
     * connections are ever reused.
     */
    static class FreshHttpClientFactory implements ClientHttpRequestFactory {
        @Override
        public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) throws IOException {
            var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build());
            factory.setReadTimeout(Duration.ofSeconds(60));
            return factory.createRequest(uri, httpMethod);
        }
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, OpenAiChatModel chatModel) {
        log.warn("╔══════════════════════════════════════════╗");
        log.warn("║  MODEL CHECK — OpenAiChatModel           ║");
        log.warn("║  defaultOptions = {}                      ║", chatModel.getDefaultOptions());
        log.warn("╚══════════════════════════════════════════╝");
        return builder.defaultOptions(chatModel.getDefaultOptions()).build();
    }

    @Bean
    public SearchProvider searchProvider(SearchConfig config) {
        String provider = config.provider();
        if (provider == null) provider = "";
        return switch (provider) {
            case "zhipu" -> {
                String key = config.zhipu().apiKey();
                if (key != null && !key.isBlank()) {
                    log.info("Search provider: ZhipuMCP (key configured)");
                    yield new ZhipuMCPSearchProvider(key);
                }
                log.warn("Search provider: NoOp (zhipu selected but no API key)");
                yield new NoOpSearchProvider();
            }
            default -> {
                log.info("Search provider: NoOp (provider='{}')", config.provider());
                yield new NoOpSearchProvider();
            }
        };
    }
}
