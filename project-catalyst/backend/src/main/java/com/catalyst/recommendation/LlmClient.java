package com.catalyst.recommendation;

public interface LlmClient {
    boolean isAvailable();

    String complete(String systemPrompt, String userPrompt);
}
