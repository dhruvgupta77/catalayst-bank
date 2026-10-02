package com.catalyst.decision;

import com.catalyst.domain.LiquidityEventType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic decision engine. Decides WHICH options are eligible and HOW MUCH each may carry.
 * The AI layer may only re-order / explain these options; it can never add or resize them.
 */
@Component
public class DecisionEngine {
    private static final BigDecimal LARGE_STEP_THRESHOLD = new BigDecimal("10000");

    private final List<OptionRule> rules;
    private final DecisionProperties props;

    public DecisionEngine(List<OptionRule> rules, DecisionProperties props) {
        this.rules = rules;
        this.props = props;
    }

    public DecisionResult decide(LiquidityEvent event, FinancialContext ctx, ProductCatalog catalog) {
        List<RuleOutcome> outcomes = new ArrayList<>();

        BigDecimal share = allocatableShare(event.eventType());
        BigDecimal allocatable = event.amount().multiply(share).setScale(2, RoundingMode.DOWN);
        outcomes.add(new RuleOutcome("EVENT_POLICY", true, event.eventType().label() + ": "
                + share.movePointRight(2).setScale(0, RoundingMode.HALF_UP) + "% of " + Money.rand(event.amount())
                + " is available for guidance (" + Money.rand(allocatable) + ")"));

        List<OptionRule.Candidate> candidates = new ArrayList<>();
        for (OptionRule rule : rules) {
            OptionRule.Evaluation ev = rule.evaluate(event, ctx, catalog);
            outcomes.add(ev.outcome());
            if (ev.candidate() != null) candidates.add(ev.candidate());
        }
        candidates.sort((a, b) -> b.weight().compareTo(a.weight()));

        BigDecimal[] amounts = allocate(allocatable, candidates, outcomes);
        applyBufferRule(ctx, candidates, amounts, allocatable, outcomes);

        List<ActionProposal> actions = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int priority = 1;
        for (int i = 0; i < candidates.size(); i++) {
            if (amounts[i].signum() <= 0) continue;
            OptionRule.Candidate c = candidates.get(i);
            actions.add(new ActionProposal(c.type(), amounts[i], priority++, c.reason(), c.product().id(),
                    c.product().name(), c.impact().apply(amounts[i])));
            total = total.add(amounts[i]);
        }
        outcomes.add(new RuleOutcome("CONSENT_REQUIRED", true,
                "Every action needs explicit customer approval before anything is executed"));

        return new DecisionResult(event.eventId(), event.customerId(), event.amount(), allocatable,
                event.amount().subtract(total).setScale(2, RoundingMode.HALF_UP), actions, outcomes);
    }

    private BigDecimal allocatableShare(LiquidityEventType type) {
        return switch (type) {
            case SALARY -> props.salaryDiscretionaryShare();
            case INSURANCE_PAYOUT -> props.insuranceAllocatableShare();
            default -> BigDecimal.ONE;
        };
    }

    private BigDecimal[] allocate(BigDecimal allocatable, List<OptionRule.Candidate> candidates, List<RuleOutcome> outcomes) {
        BigDecimal[] amounts = new BigDecimal[candidates.size()];
        java.util.Arrays.fill(amounts, BigDecimal.ZERO);
        if (candidates.isEmpty()) return amounts;

        BigDecimal step = allocatable.compareTo(LARGE_STEP_THRESHOLD) >= 0 ? new BigDecimal("1000") : new BigDecimal("100");
        BigDecimal totalWeight = candidates.stream().map(OptionRule.Candidate::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal used = BigDecimal.ZERO;
        for (int i = 0; i < candidates.size(); i++) {
            OptionRule.Candidate c = candidates.get(i);
            BigDecimal raw = allocatable.multiply(c.weight()).divide(totalWeight, 2, RoundingMode.DOWN);
            BigDecimal amt = Money.floorTo(raw, step).min(c.cap()).min(props.transactionLimit());
            amounts[i] = enforceProductLimits(c, amt, outcomes);
            used = used.add(amounts[i]);
        }
        // Rounding residue is offered to the lowest-priority option that still has headroom.
        BigDecimal residual = allocatable.subtract(used);
        for (int i = candidates.size() - 1; i >= 0 && residual.signum() > 0; i--) {
            OptionRule.Candidate c = candidates.get(i);
            BigDecimal headroom = c.cap().min(props.transactionLimit()).subtract(amounts[i]);
            BigDecimal add = Money.floorTo(residual.min(headroom), step);
            if (add.signum() > 0 && amounts[i].add(add).compareTo(c.product().minAmount()) >= 0) {
                amounts[i] = amounts[i].add(add);
                residual = residual.subtract(add);
            }
        }
        for (int i = 0; i < amounts.length; i++) amounts[i] = amounts[i].setScale(2, RoundingMode.HALF_UP);
        return amounts;
    }

    private BigDecimal enforceProductLimits(OptionRule.Candidate c, BigDecimal amt, List<RuleOutcome> outcomes) {
        if (amt.signum() > 0 && amt.compareTo(c.product().minAmount()) < 0) {
            outcomes.add(new RuleOutcome("PRODUCT_MINIMUM", false, c.product().name() + " needs at least "
                    + Money.rand(c.product().minAmount()) + "; option not offered"));
            return BigDecimal.ZERO;
        }
        return amt;
    }

    private void applyBufferRule(FinancialContext ctx, List<OptionRule.Candidate> candidates, BigDecimal[] amounts,
                                 BigDecimal allocatable, List<RuleOutcome> outcomes) {
        BigDecimal step = allocatable.compareTo(LARGE_STEP_THRESHOLD) >= 0 ? new BigDecimal("1000") : new BigDecimal("100");
        BigDecimal sum = java.util.Arrays.stream(amounts).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean trimmed = false;
        for (int i = amounts.length - 1; i >= 0 && ctx.currentBalance().subtract(sum).compareTo(props.minAccountBuffer()) < 0; i--) {
            BigDecimal excess = props.minAccountBuffer().subtract(ctx.currentBalance().subtract(sum));
            BigDecimal cut = amounts[i].min(excess.divide(step, 0, RoundingMode.UP).multiply(step));
            amounts[i] = amounts[i].subtract(cut);
            sum = sum.subtract(cut);
            trimmed = true;
        }
        outcomes.add(new RuleOutcome("MIN_ACCOUNT_BUFFER", true, trimmed
                ? "Options reduced so at least " + Money.rand(props.minAccountBuffer()) + " stays in your current account"
                : "At least " + Money.rand(props.minAccountBuffer()) + " stays in your current account"));
        outcomes.add(new RuleOutcome("TRANSACTION_LIMIT", true,
                "Each action is capped at " + Money.rand(props.transactionLimit())));
    }
}
