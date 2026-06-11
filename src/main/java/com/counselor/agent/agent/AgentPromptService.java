package com.counselor.agent.agent;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentPromptService {

    private static final Logger log = LoggerFactory.getLogger(AgentPromptService.class);

    private final ResourcePatternResolver resourceResolver;
    private final Map<String, String> prompts = new ConcurrentHashMap<>();

    public AgentPromptService(ResourcePatternResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @PostConstruct
    void loadPrompts() {
        try {
            Resource[] resources = resourceResolver.getResources("classpath*:/agents/*/prompt.md");
            for (Resource res : resources) {
                String path = res.getURL().getPath();
                String[] parts = path.split("/agents/");
                if (parts.length < 2) continue;
                String agentId = parts[1].substring(0, parts[1].indexOf('/'));
                String content = res.getContentAsString(StandardCharsets.UTF_8);
                prompts.put(agentId, content);
                log.info("Loaded prompt for agent: {} ({} chars)", agentId, content.length());
            }
            log.info("Agent prompts loaded: {}/10", prompts.size());
        } catch (IOException e) {
            log.error("Failed to load agent prompts", e);
        }
    }

    public String getPrompt(String agentId) {
        String prompt = prompts.get(agentId);
        if (prompt == null) {
            log.warn("No prompt found for agent: {}", agentId);
            return "";
        }
        return prompt;
    }

    public String getChiefPrompt() {
        return prompts.getOrDefault("chief", "");
    }
}
