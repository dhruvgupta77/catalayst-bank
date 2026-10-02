package com.catalyst.decision;

import com.catalyst.domain.ActionType;

import java.math.BigDecimal;
import java.util.function.Function;

/**
 * A deterministic eligibility rule that may yield a candidate option. No LLM is involved.
 */
public interface OptionRule {

    Evaluation evaluate(LiquidityEvent event, FinancialContext ctx, ProductCatalog catalog);

    record Candidate(ActionType type, BigDecimal weight, BigDecimal cap, Product product, String reason,
                     Function<BigDecimal, String> impact) {
    }

    record Evaluation(RuleOutcome outcome, Candidate candidate) {
        static Evaluation eligible(String rule, String detail, Candidate c) {
            return new Evaluation(new RuleOutcome(rule, true, detail), c);
        }

        static Evaluation notEligible(String rule, String detail) {
            return new Evaluation(new RuleOutcome(rule, false, detail), null);
        }
    }
}
