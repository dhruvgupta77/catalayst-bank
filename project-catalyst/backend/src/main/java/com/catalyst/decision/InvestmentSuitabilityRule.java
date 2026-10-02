package com.catalyst.decision;

import com.catalyst.domain.ActionType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

@Component
@Order(3)
public class InvestmentSuitabilityRule implements OptionRule {
    private final DecisionProperties props;

    public InvestmentSuitabilityRule(DecisionProperties props) {
        this.props = props;
    }

    @Override
    public Evaluation evaluate(LiquidityEvent event, FinancialContext ctx, ProductCatalog catalog) {
        BigDecimal coverage = ctx.emergencySavings().divide(ctx.emergencyTarget(), 4, RoundingMode.HALF_UP);
        if (coverage.compareTo(props.minEmergencyCoverageToInvest()) < 0) {
            return Evaluation.notEligible("INVESTMENT_SUITABILITY",
                    "Emergency fund coverage below " + props.minEmergencyCoverageToInvest().movePointRight(2)
                            .setScale(0, RoundingMode.HALF_UP) + "%; build a safety net first");
        }
        Product product = catalog.all().stream()
                .filter(p -> "INVESTMENT".equals(p.category()) && p.riskLevel() <= ctx.riskProfile().level())
                .max(Comparator.comparingInt(Product::riskLevel).thenComparing(Product::indicativeRate))
                .orElse(null);
        if (product == null)
            return Evaluation.notEligible("INVESTMENT_SUITABILITY", "No suitable product for risk profile");
        return Evaluation.eligible("INVESTMENT_SUITABILITY",
                product.name() + " matches " + ctx.riskProfile() + " risk profile; emergency coverage "
                        + coverage.movePointRight(2).setScale(0, RoundingMode.HALF_UP) + "%",
                new Candidate(ActionType.INVESTMENT, new BigDecimal("0.20"), product.maxAmount(), product,
                        "Long-term investment opportunity",
                        amt -> "Invested in " + product.name() + "; returns are not guaranteed and capital may fluctuate"));
    }
}
