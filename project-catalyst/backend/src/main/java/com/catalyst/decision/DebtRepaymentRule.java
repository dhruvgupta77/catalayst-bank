package com.catalyst.decision;

import com.catalyst.domain.ActionType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Order(1)
public class DebtRepaymentRule implements OptionRule {
    private final DecisionProperties props;

    public DebtRepaymentRule(DecisionProperties props) {
        this.props = props;
    }

    @Override
    public Evaluation evaluate(LiquidityEvent event, FinancialContext ctx, ProductCatalog catalog) {
        BigDecimal bal = ctx.creditCardBalance();
        BigDecimal rate = ctx.creditCardRate();
        if (bal == null || rate == null) {
            return Evaluation.notEligible("HIGH_INTEREST_DEBT", "No credit card data available");
        }
        if (bal.signum() <= 0 || rate.compareTo(props.highInterestThreshold()) < 0) {
            return Evaluation.notEligible("HIGH_INTEREST_DEBT",
                    "No debt at or above " + props.highInterestThreshold() + "% p.a.");
        }
        Product product = catalog.byId("PRD-DEBT").orElse(null);
        if (product == null) return Evaluation.notEligible("HIGH_INTEREST_DEBT", "Debt repayment product unavailable");
        String qualifier = ctx.reducedContext() ? " (based on last known balance)" : "";
        return Evaluation.eligible("HIGH_INTEREST_DEBT",
                "Credit card " + Money.rand(bal) + " at " + rate + "% p.a. is at or above the "
                        + props.highInterestThreshold() + "% threshold" + qualifier,
                new Candidate(ActionType.DEBT_REPAYMENT, new BigDecimal("0.45"), bal, product,
                        "High-interest debt reduction",
                        amt -> "Could save roughly " + Money.rand(amt.multiply(rate).divide(new BigDecimal("100"), 0,
                                RoundingMode.HALF_UP)) + " in interest over 12 months at the current rate"));
    }
}
