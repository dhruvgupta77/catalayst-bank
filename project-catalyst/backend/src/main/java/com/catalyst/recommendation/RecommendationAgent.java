package com.catalyst.recommendation;

/**
 * Explains and ranks options that the rule engine already declared eligible.
 * Deliberately has NO dependency on consent, execution or account services.
 */
public interface RecommendationAgent {
    AiRecommendation recommend(AgentInput input) throws AgentException;
}
