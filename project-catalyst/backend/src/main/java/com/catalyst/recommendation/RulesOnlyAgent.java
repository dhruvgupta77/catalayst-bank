package com.catalyst.recommendation;

/**
 * Fallback when AI is unavailable or its output is rejected: plain, rule-derived wording, no AI confidence.
 */
public class RulesOnlyAgent implements RecommendationAgent {
    @Override
    public AiRecommendation recommend(AgentInput in) {
        return NarrativeComposer.compose(in, null, false);
    }
}
