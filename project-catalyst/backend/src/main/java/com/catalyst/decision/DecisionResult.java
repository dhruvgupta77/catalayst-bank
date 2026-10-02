package com.catalyst.decision;

import java.math.BigDecimal;
import java.util.List;

public record DecisionResult(String eventId, String customerId, BigDecimal eventAmount, BigDecimal allocatable,
                             BigDecimal keptInAccount, List<ActionProposal> recommendedActions,
                             List<RuleOutcome> ruleOutcomes) {
}
