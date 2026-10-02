package com.catalyst.application;

import com.catalyst.api.NotFoundException;
import com.catalyst.audit.AuditService;
import com.catalyst.consent.ConsentRequiredException;
import com.catalyst.consent.ConsentService;
import com.catalyst.customer.BureauCache;
import com.catalyst.customer.CreditBureauReport;
import com.catalyst.demo.CatalystMetrics;
import com.catalyst.demo.CatalystProperties;
import com.catalyst.demo.DemoState;
import com.catalyst.domain.*;
import com.catalyst.infrastructure.persistence.Repositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Mock core-banking executor. Refuses to run any action that lacks valid customer consent.
 */
@Service
public class ActionExecutionService {
    private static final Logger log = LoggerFactory.getLogger(ActionExecutionService.class);

    private final FinancialActionRepository actions;
    private final AccountRepository accounts;
    private final LiquidityEventRepository events;
    private final ConsentService consent;
    private final AuditService audit;
    private final CatalystMetrics metrics;
    private final CatalystProperties props;
    private final BureauCache bureauCache;
    private final TransactionTemplate tx;
    private final DemoState demoState;

    public ActionExecutionService(FinancialActionRepository actions, AccountRepository accounts,
                                  LiquidityEventRepository events, ConsentService consent, AuditService audit,
                                  CatalystMetrics metrics, CatalystProperties props, BureauCache bureauCache,
                                  TransactionTemplate tx, DemoState demoState) {
        this.actions = actions;
        this.accounts = accounts;
        this.events = events;
        this.consent = consent;
        this.audit = audit;
        this.metrics = metrics;
        this.props = props;
        this.bureauCache = bureauCache;
        this.tx = tx;
        this.demoState = demoState;
    }

    /**
     * Bus consumer entry point; errors are recorded, not rethrown.
     */
    public void executeApproved(String actionId, String correlationId) {
        try {
            execute(actionId);
        } catch (RuntimeException e) {
            log.warn("Execution of {} not performed: {}", actionId, e.getMessage());
        }
    }

    public FinancialActionEntity execute(String actionId) {
        FinancialActionEntity a = actions.findById(actionId).orElseThrow(() -> new NotFoundException("Action not found"));
        String corr = events.findById(a.getEventId()).map(LiquidityEventEntity::getCorrelationId).orElse("COR-UNKNOWN");
        MDC.put("correlationId", corr);
        MDC.put("eventId", a.getEventId());
        try {
            if (a.getStatus() == ActionStatus.EXECUTED) return a;
            try {
                consent.requireValid(a);
                if (a.getStatus() != ActionStatus.APPROVED && a.getStatus() != ActionStatus.EXECUTING) {
                    throw new ConsentRequiredException("Action " + a.getId() + " is not approved (status " + a.getStatus() + ")");
                }
            } catch (ConsentRequiredException e) {
                audit.record(a.getEventId(), a.getCustomerId(), corr, "ACTION_REJECTED", "SYSTEM", "DENIED",
                        "Execution refused: valid customer consent is required");
                throw e;
            }
            // Deterministic policy is re-checked at execution time, after consent: consent never overrides compliance.
            if (demoState.isComplianceBlock()) {
                if (a.getStatus() == ActionStatus.APPROVED && actions.transition(actionId, ActionStatus.APPROVED, ActionStatus.FAILED) == 1) {
                    tx.executeWithoutResult(s -> {
                        FinancialActionEntity x = actions.findById(actionId).orElseThrow();
                        x.setFailureReason("Blocked by compliance rule (demo compliance hold). No money moved.");
                        actions.save(x);
                    });
                    audit.record(a.getEventId(), a.getCustomerId(), corr, "ACTION_BLOCKED", "RULE_ENGINE", "DENIED",
                            "COMPLIANCE_SCREENING failed (demo compliance hold): action blocked despite customer consent");
                }
                return actions.findById(actionId).orElseThrow();
            }
            if (actions.transition(actionId, ActionStatus.APPROVED, ActionStatus.EXECUTING) == 0) {
                return actions.findById(actionId).orElseThrow(); // already picked up by another delivery
            }
            try {
                step(actionId, 25, "Consent verified");
                step(actionId, 50, "Funds reserved");
                step(actionId, 75, "Posting to core banking (mock)");
                post(actionId);
                audit.record(a.getEventId(), a.getCustomerId(), corr, "ACTION_EXECUTED", "SYSTEM", "OK",
                        a.getActionType() + " executed after customer consent (" + a.getId() + ")");
                metrics.inc("actions.executed");
            } catch (RuntimeException e) {
                fail(actionId, "Execution failed: " + e.getMessage());
                audit.record(a.getEventId(), a.getCustomerId(), corr, "ACTION_FAILED", "SYSTEM", "FAILURE",
                        "Execution failed: " + e.getMessage());
                metrics.inc("actions.failed");
            }
            return actions.findById(actionId).orElseThrow();
        } finally {
            MDC.remove("correlationId");
            MDC.remove("eventId");
        }
    }

    private void step(String id, int progress, String note) {
        tx.executeWithoutResult(s -> {
            FinancialActionEntity a = actions.findById(id).orElseThrow();
            a.setProgress(progress);
            a.setProgressNote(note);
            actions.save(a);
        });
        if (props.executionStepDelayMs() > 0) {
            try {
                Thread.sleep(props.executionStepDelayMs());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void post(String id) {
        tx.executeWithoutResult(s -> {
            FinancialActionEntity a = actions.findById(id).orElseThrow();
            AccountEntity current = accounts.findByCustomerIdAndType(a.getCustomerId(), AccountType.CURRENT).orElseThrow();
            AccountType target = switch (a.getActionType()) {
                case DEBT_REPAYMENT -> AccountType.CREDIT_CARD;
                case SAVINGS -> AccountType.SAVINGS;
                case INVESTMENT -> AccountType.INVESTMENT;
            };
            AccountEntity t = accounts.findByCustomerIdAndType(a.getCustomerId(), target).orElseThrow();
            if (accounts.debit(current.getId(), a.getAmount()) == 0)
                throw new IllegalStateException("insufficient funds");
            if (target == AccountType.CREDIT_CARD) {
                if (accounts.debit(t.getId(), a.getAmount()) == 0)
                    throw new IllegalStateException("repayment exceeds outstanding balance");
                AccountEntity card = accounts.findById(t.getId()).orElseThrow();
                bureauCache.put(new CreditBureauReport(a.getCustomerId(), card.getBalance(), card.getInterestRate(),
                        bureauCache.get(a.getCustomerId()).map(CreditBureauReport::creditScore).orElse(0)));
            } else {
                accounts.credit(t.getId(), a.getAmount());
            }
            a.setStatus(ActionStatus.EXECUTED);
            a.setProgress(100);
            a.setProgressNote("Completed");
            a.setExecutedAt(Instant.now());
            actions.save(a);
        });
    }

    private void fail(String id, String reason) {
        tx.executeWithoutResult(s -> {
            FinancialActionEntity a = actions.findById(id).orElseThrow();
            a.setStatus(ActionStatus.FAILED);
            a.setFailureReason(reason.length() > 380 ? reason.substring(0, 380) : reason);
            actions.save(a);
        });
    }
}

