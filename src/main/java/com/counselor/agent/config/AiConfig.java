package com.counselor.agent.config;

import com.counselor.agent.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /**
     * RestClient.Builder for DeepSeek API calls.
     * Creates a NEW HttpClient per request — no connection pooling.
     * This avoids stale-connection bugs where pooled connections die after
     * idle periods but the client doesn't detect it until timeout.
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
        factory.setReadTimeout(Duration.ofSeconds(60));

        return RestClient.builder()
                .requestFactory(factory)
                .requestInterceptor(new ReasoningCaptureInterceptor());
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
