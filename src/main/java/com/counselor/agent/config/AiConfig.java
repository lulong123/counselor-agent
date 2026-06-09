package com.counselor.agent.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, OpenAiChatModel chatModel) {
        log.warn("╔══════════════════════════════════════════╗");
        log.warn("║  MODEL CHECK — OpenAiChatModel           ║");
        log.warn("║  defaultOptions = {}                      ║", chatModel.getDefaultOptions());
        log.warn("╚══════════════════════════════════════════╝");
        return builder.defaultOptions(chatModel.getDefaultOptions()).build();
    }
}
