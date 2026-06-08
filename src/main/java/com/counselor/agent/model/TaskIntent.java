package com.counselor.agent.model;

public record TaskIntent(
        String agentId,
        double confidence,
        String risk,
        String reasoning
) {
    public static final String RISK_LOW = "low";
    public static final String RISK_MEDIUM = "medium";
    public static final String RISK_HIGH = "high";

    public boolean isRouted() {
        return agentId != null && !agentId.isBlank();
    }
}
