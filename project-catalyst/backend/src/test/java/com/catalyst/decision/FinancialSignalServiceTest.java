package com.catalyst.decision;

import static com.catalyst.decision.TestData.bd;
import static com.catalyst.decision.TestData.thabo;
import static org.assertj.core.api.Assertions.assertThat;

import com.catalyst.domain.RiskProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinancialSignalServiceTest {
    private final FinancialSignalService service =
            new FinancialSignalService(new DecisionProperties(null, null, null, null, null, null));

    private List<String> ids(List<FinancialSignal> s) {
        return s.stream().map(FinancialSignal::id).toList();
    }

    @Test
    void thaboShowsTransparentRuleBasedSignals() {
        List<FinancialSignal> signals = service.assess(thabo("8500"));
        assertThat(ids(signals)).containsExactlyInAnyOrder("HIGH_REVOLVING_DEBT", "LIMITED_EMERGENCY_BUFFER",
                "DEBT_TO_INCOME_CONCERN", "LOW_AVAILABLE_BALANCE");
        assertThat(signals).allSatisfy(s -> assertThat(s.basis()).isNotBlank());
        assertThat(FinancialSignalService.LABEL).contains("Illustrative").contains("not a predictive model");
    }

    @Test
    void inflowIsRemovedSoSignalsDescribeThePreEventSituation() {
        assertThat(ids(service.assess(thabo("53500")))).doesNotContain("LOW_AVAILABLE_BALANCE");
        assertThat(ids(service.assessBeforeEvent(thabo("53500"), bd("45000")))).contains("LOW_AVAILABLE_BALANCE");
    }

    @Test
    void healthyCustomerHasNoSignals() {
        FinancialContext healthy = new FinancialContext("CUST-1", "X", bd("42000"), RiskProfile.MODERATE, bd("60000"),
                bd("60000"), bd("50000"), bd("35000"), bd("0"), bd("22"), 800, bd("0"), bd("0"), bd("0"), false, List.of());
        assertThat(service.assess(healthy)).isEmpty();
    }

    @Test
    void missingBureauDataDoesNotInventDebtSignals() {
        FinancialContext noBureau = new FinancialContext("CUST-1", "X", bd("42000"), RiskProfile.MODERATE, bd("60000"),
                bd("60000"), bd("50000"), bd("35000"), null, null, null, bd("0"), bd("0"), bd("0"), true, List.of());
        assertThat(ids(service.assess(noBureau))).doesNotContain("HIGH_REVOLVING_DEBT");
    }
}