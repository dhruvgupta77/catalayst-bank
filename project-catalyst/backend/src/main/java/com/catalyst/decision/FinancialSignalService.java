package com.catalyst.decision;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * "Financial vulnerability" detection. Deliberately deterministic: simple thresholds over the demo data,
 * labelled as illustrative. There is no predictive model and no transaction history in this demo.
 */
@Service
public class FinancialSignalService {
    public static final String LABEL = "Illustrative demo signals (rule-based, not a predictive model)";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEBT_TO_INCOME_THRESHOLD = new BigDecimal("20");
    private static final BigDecimal LOW_BALANCE_MONTHS = new BigDecimal("0.25");

    private final DecisionProperties props;

    public FinancialSignalService(DecisionProperties props) {
        this.props = props;
    }

    /** The inflow is already credited when the context is enriched; signals describe the situation before it arrived. */
    public List<FinancialSignal> assessBeforeEvent(FinancialContext c, BigDecimal eventAmount) {
        return assess(new FinancialContext(c.customerId(), c.customerName(), c.monthlySalary(), c.riskProfile(),
                c.currentBalance().subtract(eventAmount).max(BigDecimal.ZERO), c.emergencySavings(), c.emergencyTarget(),
                c.investmentBalance(), c.creditCardBalance(), c.creditCardRate(), c.creditScore(), c.loanBalance(),
                c.loanRate(), c.monthlyLoanPayment(), c.reducedContext(), c.notes()));
    }

    public List<FinancialSignal> assess(FinancialContext c) {
        List<FinancialSignal> out = new ArrayList<>();

        if (c.creditCardBalance() != null && c.creditCardRate() != null && c.creditCardBalance().signum() > 0
                && c.creditCardRate().compareTo(props.highInterestThreshold()) >= 0) {
            out.add(new FinancialSignal("HIGH_REVOLVING_DEBT", "High revolving debt", "HIGH",
                    "Credit card " + Money.rand(c.creditCardBalance()) + " at " + c.creditCardRate().stripTrailingZeros().toPlainString()
                            + "% p.a." + (c.reducedContext() ? " (last known balance)" : ""),
                    "Card balance > 0 and rate >= " + props.highInterestThreshold() + "% p.a."));
        }

        if (c.emergencyTarget().signum() > 0) {
            BigDecimal coverage = c.emergencySavings().multiply(HUNDRED).divide(c.emergencyTarget(), 0, RoundingMode.HALF_UP);
            if (coverage.compareTo(HUNDRED) < 0) {
                out.add(new FinancialSignal("LIMITED_EMERGENCY_BUFFER", "Limited emergency buffer",
                        coverage.compareTo(new BigDecimal("50")) < 0 ? "HIGH" : "MEDIUM",
                        Money.rand(c.emergencySavings()) + " saved of a " + Money.rand(c.emergencyTarget()) + " target (" + coverage + "%)",
                        "Emergency savings < emergency fund target"));
            }
        }

        if (c.monthlySalary().signum() > 0) {
            BigDecimal debt = c.loanBalance() == null ? BigDecimal.ZERO : c.loanBalance();
            if (c.creditCardBalance() != null) debt = debt.add(c.creditCardBalance());
            BigDecimal annual = c.monthlySalary().multiply(BigDecimal.valueOf(12));
            BigDecimal ratio = debt.multiply(HUNDRED).divide(annual, 0, RoundingMode.HALF_UP);
            if (ratio.compareTo(DEBT_TO_INCOME_THRESHOLD) >= 0) {
                BigDecimal payments = c.monthlyLoanPayment() == null ? BigDecimal.ZERO : c.monthlyLoanPayment();
                out.add(new FinancialSignal("DEBT_TO_INCOME_CONCERN", "Debt-to-income concern", "MEDIUM",
                        "Total debt " + Money.rand(debt) + " is " + ratio + "% of annual income; loan repayments are "
                                + payments.multiply(HUNDRED).divide(c.monthlySalary(), 0, RoundingMode.HALF_UP) + "% of monthly income",
                        "Total debt / annual income >= " + DEBT_TO_INCOME_THRESHOLD + "%"));
            }

            BigDecimal months = c.currentBalance().divide(c.monthlySalary(), 2, RoundingMode.HALF_UP);
            if (months.compareTo(LOW_BALANCE_MONTHS) < 0) {
                out.add(new FinancialSignal("LOW_AVAILABLE_BALANCE", "Low available balance", "MEDIUM",
                        "Current account " + Money.rand(c.currentBalance()) + " is about "
                                + months.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP) + "% of one month's salary",
                        "Current balance < 25% of monthly salary"));
            }
        }
        return out;
    }
}