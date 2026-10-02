package com.catalyst.recommendation;

import com.catalyst.decision.ActionProposal;

import java.util.List;

/**
 * Final ordered actions plus narrative, and which path produced the narrative.
 */
public record RecommendationOutcome(List<ActionProposal> orderedActions, AiRecommendation narrative, String source,
                                    String fallbackReason) {
}
