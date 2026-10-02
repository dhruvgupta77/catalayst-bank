package com.catalyst.recommendation;

import java.util.List;

/**
 * Structured narrative. Amounts always come from the rule engine, never from the model.
 */
public record AiRecommendation(String summary, String recommendation, List<String> reasoning,
                               List<String> alternatives, Double confidence, List<String> rankedOptions) {
}
