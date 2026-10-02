package com.catalyst.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * bus: "memory" (single JVM, no Kafka needed) or "kafka".
 * aiMode: "scripted" (deterministic, offline) or "llm" (Spring AI: Azure OpenAI / Ollama).
 */
@ConfigurationProperties(prefix = "catalyst")
public record CatalystProperties(String bus, String aiMode, long latencyTargetMs, long executionStepDelayMs,
                                 long consentTtlMinutes) {
    public CatalystProperties {
        if (bus == null) bus = "memory";
        if (aiMode == null) aiMode = "scripted";
        if (latencyTargetMs <= 0) latencyTargetMs = 2000;
        if (consentTtlMinutes <= 0) consentTtlMinutes = 15;
    }
}
