package com.counselor.agent.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentRouter {

    private final Map<String, SubAgent> agentMap;

    public AgentRouter(List<SubAgent> agents) {
        this.agentMap = agents.stream()
                .collect(Collectors.toMap(SubAgent::getId, Function.identity()));
    }

    public Optional<SubAgent> dispatch(String agentId) {
        return Optional.ofNullable(agentMap.get(agentId));
    }

    public List<SubAgent> allAgents() {
        return List.copyOf(agentMap.values());
    }
}
