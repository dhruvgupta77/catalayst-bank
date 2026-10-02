package com.catalyst.recommendation;

import com.catalyst.decision.ActionProposal;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The minimum context the agent receives: no name, no customer id, no account numbers, no free-text memo.
 */
public record AgentInput(String eventLabel, BigDecimal eventAmount, BigDecimal keptInAccount,
                         List<ActionProposal> options, String riskProfile, Integer emergencyCoveragePercent,
                         BigDecimal creditCardBalance, BigDecimal creditCardRate, BigDecimal emergencySavings,
                         BigDecimal emergencyTarget, boolean reducedContext) {

    /**
     * Numbers the narrative is allowed to mention.
     */
    public Set<Long> allowedAmounts() {
        Set<Long> s = new HashSet<>();
        s.add(eventAmount.longValue());
        s.add(keptInAccount.longValue());
        if (creditCardBalance != null) s.add(creditCardBalance.longValue());
        if (emergencySavings != null) s.add(emergencySavings.longValue());
        if (emergencyTarget != null) s.add(emergencyTarget.longValue());
        options.forEach(o -> s.add(o.amount().longValue()));
        return s;
    }
}

