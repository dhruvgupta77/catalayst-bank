package com.catalyst.decision;

import com.catalyst.domain.ActionType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Order(2)
public class EmergencySavingsRule implements OptionRule {

    @Override
    public Evaluation evaluate(LiquidityEvent event, FinancialContext ctx, ProductCatalog catalog) {
        BigDecimal gap = ctx.emergencyTarget().subtract(ctx.emergencySavings());
        if (gap.signum() <= 0) {
            return Evaluation.notEligible("EMERGENCY_FUND_GAP", "Emergency fund has reached its target");
        }
        Product product = catalog.byId("PRD-SAVINGS").orElse(null);
        if (product == null) return Evaluation.notEligible("EMERGENCY_FUND_GAP", "Savings product unavailable");
        return Evaluation.eligible("EMERGENCY_FUND_GAP",
                "Emergency fund " + Money.rand(ctx.emergencySavings()) + " is below the " + Money.rand(ctx.emergencyTarget())
                        + " target (gap " + Money.rand(gap) + ")",
                new Candidate(ActionType.SAVINGS, new BigDecimal("0.35"), gap, product, "Emergency fund below target",
                        amt -> "Would take your emergency fund to "
                                + ctx.emergencySavings().add(amt).multiply(new BigDecimal("100"))
                                .divide(ctx.emergencyTarget(), 0, RoundingMode.HALF_UP) + "% of target"));
    }
}
