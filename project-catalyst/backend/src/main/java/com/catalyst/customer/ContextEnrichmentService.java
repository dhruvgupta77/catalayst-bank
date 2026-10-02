package com.catalyst.customer;

import com.catalyst.decision.FinancialContext;
import com.catalyst.decision.LiquidityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContextEnrichmentService {
    private static final Logger log = LoggerFactory.getLogger(ContextEnrichmentService.class);

    private final CustomerProfileClient profileClient;
    private final CreditBureauClient bureauClient;
    private final BureauCache cache;

    public ContextEnrichmentService(CustomerProfileClient profileClient, CreditBureauClient bureauClient, BureauCache cache) {
        this.profileClient = profileClient;
        this.bureauClient = bureauClient;
        this.cache = cache;
    }

    /**
     * Warm the fallback cache so "bureau down" has last-known-good data.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmCache() {
        try {
            cache.put(bureauClient.fetch("CUST-1001"));
        } catch (RuntimeException e) {
            log.warn("Bureau cache warm-up skipped: {}", e.getMessage());
        }
    }

    public FinancialContext enrich(LiquidityEvent event) {
        CustomerProfile p = profileClient.fetch(event.customerId());
        List<String> notes = new ArrayList<>();
        CreditBureauReport report = null;
        boolean reduced = false;
        try {
            report = bureauClient.fetch(event.customerId());
            cache.put(report);
        } catch (ExternalDependencyException e) {
            reduced = true;
            notes.add("External dependency unavailable");
            report = cache.get(event.customerId()).orElse(null);
            notes.add(report != null ? "Using cached customer context" : "No cached bureau data available");
            notes.add("Recommendation generated with reduced context");
            log.warn("Credit bureau unavailable for event {} - degrading gracefully", event.eventId());
        }
        return new FinancialContext(p.customerId(), p.name(), p.monthlySalary(), p.riskProfile(), p.currentBalance(),
                p.emergencySavings(), p.emergencyTarget(), p.investmentBalance(),
                report == null ? null : report.creditCardBalance(),
                report == null ? null : report.creditCardInterestRate(),
                report == null ? null : report.creditScore(),
                p.loanBalance(), p.loanRate(), p.monthlyLoanPayment(), reduced, notes);
    }
}
