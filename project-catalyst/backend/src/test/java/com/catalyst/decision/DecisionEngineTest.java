package com.catalyst.decision;

import static com.catalyst.decision.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.catalyst.domain.ActionType;
import com.catalyst.domain.LiquidityEventType;
import com.catalyst.domain.RiskProfile;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class DecisionEngineTest {
    private final DecisionEngine engine = engine();

    private static void assertAction(DecisionResult r, int idx, ActionType type, String amount) {
        ActionProposal a = r.recommendedActions().get(idx);
        assertThat(a.type()).isEqualTo(type);
        assertThat(a.amount()).isEqualByComparingTo(amount);
        assertThat(a.priority()).isEqualTo(idx + 1);
    }

    @Test
    void twoPotProducesDebtSavingsInvestmentSplit() {
        DecisionResult r = engine.decide(event(LiquidityEventType.TWO_POT_PAYOUT, "45000"), thabo("53500"), catalog());
        assertThat(r.recommendedActions()).hasSize(3);
        assertAction(r, 0, ActionType.DEBT_REPAYMENT, "20000");
        assertAction(r, 1, ActionType.SAVINGS, "15000");
        assertAction(r, 2, ActionType.INVESTMENT, "10000");
        assertThat(r.keptInAccount()).isEqualByComparingTo("0");
    }

    @Test
    void salaryProducesSmallerDiscretionaryRecommendation() {
        DecisionResult r = engine.decide(event(LiquidityEventType.SALARY, "42000"), thabo("50500"), catalog());
        assertThat(r.allocatable()).isEqualByComparingTo("6300");
        assertThat(r.recommendedActions()).hasSize(3);
        BigDecimal total = r.recommendedActions().stream().map(ActionProposal::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("6300");
        assertThat(r.keptInAccount()).isEqualByComparingTo("35700");
    }

    @Test
    void insuranceHoldsBackHalfForTheInsuredLoss() {
        DecisionResult r = engine.decide(event(LiquidityEventType.INSURANCE_PAYOUT, "75000"), thabo("83500"), catalog());
        assertThat(r.allocatable()).isEqualByComparingTo("37500");
        assertThat(r.keptInAccount()).isGreaterThanOrEqualTo(bd("37500"));
    }

    @Test
    void differentEventsGiveDifferentRecommendations() {
        var bonus = engine.decide(event(LiquidityEventType.BONUS, "15000"), thabo("23500"), catalog());
        var tax = engine.decide(event(LiquidityEventType.TAX_REFUND, "18500"), thabo("27000"), catalog());
        assertThat(bonus.recommendedActions()).isNotEqualTo(tax.recommendedActions());
        assertThat(bonus.recommendedActions()).hasSize(3);
    }

    @Test
    void noDebtOptionWhenCardIsPaidOff() {
        FinancialContext c = thabo("53500");
        FinancialContext noDebt = new FinancialContext(c.customerId(), c.customerName(), c.monthlySalary(), c.riskProfile(),
                c.currentBalance(), c.emergencySavings(), c.emergencyTarget(), c.investmentBalance(), bd("0"), bd("22"),
                742, c.loanBalance(), c.loanRate(), c.monthlyLoanPayment(), false, List.of());
        var r = engine.decide(event(LiquidityEventType.TWO_POT_PAYOUT, "45000"), noDebt, catalog());
        assertThat(r.recommendedActions()).noneMatch(a -> a.type() == ActionType.DEBT_REPAYMENT);
    }

    @Test
    void noDebtOptionWhenBureauDataMissing() {
        FinancialContext c = thabo("53500");
        FinancialContext reduced = new FinancialContext(c.customerId(), c.customerName(), c.monthlySalary(), c.riskProfile(),
                c.currentBalance(), c.emergencySavings(), c.emergencyTarget(), c.investmentBalance(), null, null, null,
                c.loanBalance(), c.loanRate(), c.monthlyLoanPayment(), true, List.of("No cached bureau data available"));
        var r = engine.decide(event(LiquidityEventType.TWO_POT_PAYOUT, "45000"), reduced, catalog());
        assertThat(r.recommendedActions()).noneMatch(a -> a.type() == ActionType.DEBT_REPAYMENT);
    }

    @Test
    void investmentRequiresMinimumEmergencyCoverage() {
        FinancialContext c = thabo("53500");
        FinancialContext thin = new FinancialContext(c.customerId(), c.customerName(), c.monthlySalary(), c.riskProfile(),
                c.currentBalance(), bd("5000"), c.emergencyTarget(), c.investmentBalance(), c.creditCardBalance(),
                c.creditCardRate(), 742, c.loanBalance(), c.loanRate(), c.monthlyLoanPayment(), false, List.of());
        var r = engine.decide(event(LiquidityEventType.TWO_POT_PAYOUT, "45000"), thin, catalog());
        assertThat(r.recommendedActions()).noneMatch(a -> a.type() == ActionType.INVESTMENT);
        assertThat(r.ruleOutcomes()).anyMatch(o -> o.ruleId().equals("INVESTMENT_SUITABILITY") && !o.passed());
    }

    @Test
    void conservativeRiskProfileNeverGetsModerateRiskProduct() {
        FinancialContext c = thabo("53500");
        FinancialContext cons = new FinancialContext(c.customerId(), c.customerName(), c.monthlySalary(), RiskProfile.CONSERVATIVE,
                c.currentBalance(), c.emergencySavings(), c.emergencyTarget(), c.investmentBalance(), c.creditCardBalance(),
                c.creditCardRate(), 742, c.loanBalance(), c.loanRate(), c.monthlyLoanPayment(), false, List.of());
        var r = engine.decide(event(LiquidityEventType.TWO_POT_PAYOUT, "45000"), cons, catalog());
        assertThat(r.recommendedActions()).filteredOn(a -> a.type() == ActionType.INVESTMENT)
                .allMatch(a -> a.productId().equals("PRD-MONEY-MKT"));
    }

    @Test
    void minimumAccountBufferIsRespected() {
        // Current balance after inflow is barely above the payout, so options must be trimmed.
        DecisionResult r = engine.decide(event(LiquidityEventType.TWO_POT_PAYOUT, "45000"), thabo("46000"), catalog());
        BigDecimal total = r.recommendedActions().stream().map(ActionProposal::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(bd("46000").subtract(total)).isGreaterThanOrEqualTo(bd("2000"));
    }

    @Test
    void consentRuleIsAlwaysPresent() {
        var r = engine.decide(event(LiquidityEventType.BONUS, "15000"), thabo("23500"), catalog());
        assertThat(r.ruleOutcomes()).anyMatch(o -> o.ruleId().equals("CONSENT_REQUIRED") && o.passed());
    }

    @Test
    void amountsNeverExceedTransactionLimit() {
        DecisionResult r = engine.decide(event(LiquidityEventType.BONUS, "5000000"), thabo("5008500"), catalog());
        assertThat(r.recommendedActions()).allMatch(a -> a.amount().compareTo(bd("100000")) <= 0);
    }
}