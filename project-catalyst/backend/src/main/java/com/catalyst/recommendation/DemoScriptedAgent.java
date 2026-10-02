package com.catalyst.recommendation;

/**
 * Deterministic, offline stand-in for the LLM so the demo needs no credentials or internet.
 */
public class DemoScriptedAgent implements RecommendationAgent {
    @Override
    public AiRecommendation recommend(AgentInput in) {
        return NarrativeComposer.compose(in, in.reducedContext() ? 0.62 : 0.87, true);
    }
}
