package com.catalyst.application;

import com.catalyst.api.Dtos.*;
import com.catalyst.api.NotFoundException;
import com.catalyst.audit.AuditService;
import com.catalyst.customer.ContextEnrichmentService;
import com.catalyst.decision.FinancialContext;
import com.catalyst.decision.FinancialSignalService;
import com.catalyst.decision.LiquidityEvent;
import com.catalyst.demo.CatalystProperties;
import com.catalyst.domain.AuditEventEntity;
import com.catalyst.domain.LiquidityEventEntity;
import com.catalyst.domain.LiquidityEventType;
import com.catalyst.infrastructure.persistence.Repositories.LiquidityEventRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Read models for the story UI: the customer's financial context with signals, and a pipeline/event trace
 * derived purely from the audit trail (so what is shown is exactly what was recorded).
 */
@Service
public class PipelineService {
    private static final String PRINCIPLE = "AI recommends. Rules constrain. The customer decides. The system audits.";

    private final ContextEnrichmentService enrichment;
    private final FinancialSignalService signals;
    private final AuditService audit;
    private final LiquidityEventRepository events;
    private final CatalystProperties props;

    public PipelineService(ContextEnrichmentService enrichment, FinancialSignalService signals, AuditService audit,
                           LiquidityEventRepository events, CatalystProperties props) {
        this.enrichment = enrichment;
        this.signals = signals;
        this.audit = audit;
        this.events = events;
        this.props = props;
    }

    public FinancialContextView financialContext(String customerId) {
        FinancialContext c = enrichment.enrich(new LiquidityEvent("PREVIEW", LiquidityEventType.TWO_POT_PAYOUT, customerId,
                BigDecimal.ZERO, "ZAR", Instant.now(), "NONE"));
        List<ContextSource> sources = List.of(
                new ContextSource("Customer profile", "MOCK_PROFILE_API", "OK", "Income, balances, savings, investments, risk profile"),
                new ContextSource("Credit bureau", "MOCK_CREDIT_BUREAU", c.reducedContext() ? "CACHED" : "OK",
                        c.reducedContext() ? "Bureau unavailable: using last known values" : "Card balance, interest rate, credit score"),
                new ContextSource("Product catalogue", "PRODUCT_CATALOG", "OK", "Products the customer could be offered"));
        return new FinancialContextView(FinancialSignalService.LABEL, c.customerId(), c.customerName(), c.monthlySalary(),
                c.currentBalance(), c.emergencySavings(), c.emergencyTarget(), c.investmentBalance(), c.creditCardBalance(),
                c.creditCardRate(), c.loanBalance(), c.monthlyLoanPayment(), c.riskProfile().name(), c.reducedContext(),
                c.notes(), signals.assess(c), sources,
                "Fictional demo data and illustrative rule-based signals. Not financial advice and not a predictive model.");
    }

    public PipelineView pipeline(String eventId) {
        LiquidityEventEntity ev = events.findById(eventId).orElseThrow(() -> new NotFoundException("Event " + eventId + " not found"));
        List<AuditEventEntity> trail = audit.trail(eventId);
        Instant first = trail.isEmpty() ? ev.getReceivedAt() : trail.get(0).getCreatedAt();
        Lookup t = new Lookup(trail, first);

        Optional<AuditEventEntity> ctx = t.find("CONTEXT_ENRICHED");
        Optional<AuditEventEntity> gen = t.find("RECOMMENDATION_GENERATED");
        boolean published = t.has("RECOMMENDATION_PUBLISHED");
        boolean degraded = ctx.map(a -> "DEGRADED".equals(a.getResult())).orElse(false);
        boolean fallback = gen.map(a -> a.getDetail().contains("fallback=")).orElse(false);

        List<PipelineStage> stages = new ArrayList<>();
        stages.add(stage("DETECT", "Detect", t, "EVENT_RECEIVED", "DONE", "Liquidity event received"));
        stages.add(new PipelineStage("UNDERSTAND", "Understand", ctx.isPresent() ? (degraded ? "DEGRADED" : "DONE") : "PENDING",
                t.at("SIGNALS_DETECTED", "CONTEXT_ENRICHED"),
                ctx.map(AuditEventEntity::getDetail).orElse("Waiting for customer context")));
        stages.add(stage("DECIDE", "Decide", t, "RULES_EVALUATED", "DONE", "Rules evaluated"));
        stages.add(new PipelineStage("RECOMMEND", "Recommend", published ? (fallback ? "DEGRADED" : "DONE") : "PENDING",
                t.at("RECOMMENDATION_PUBLISHED"), gen.map(AuditEventEntity::getDetail).orElse("Waiting for recommendation")));

        boolean declined = t.has("CUSTOMER_DECLINED");
        boolean consented = t.has("CUSTOMER_APPROVED");
        boolean blockedByPolicy = t.has("POLICY_BLOCKED") && !consented;
        String consentState = consented ? "DONE" : declined ? "DECLINED" : blockedByPolicy ? "BLOCKED" : published ? "WAITING" : "PENDING";
        String consentDetail = consented ? "Customer consent recorded"
                : declined ? "Customer declined the recommendation"
                : blockedByPolicy ? "A policy rule blocked the customer's change"
                : published ? "Waiting for the customer to accept, modify or decline" : "Not reached yet";
        stages.add(new PipelineStage("CONSENT", "Consent", consentState,
                t.at("CUSTOMER_APPROVED", "CUSTOMER_DECLINED", "POLICY_BLOCKED"), consentDetail));

        String actState = t.has("ACTION_BLOCKED") ? "BLOCKED" : t.has("ACTION_FAILED") ? "FAILED"
                : t.has("ACTION_EXECUTED") ? "DONE" : t.has("NO_ACTION_EXECUTED") ? "SKIPPED" : "PENDING";
        String actDetail = switch (actState) {
            case "BLOCKED" -> "Blocked by a deterministic compliance rule; nothing was executed";
            case "FAILED" -> "Execution failed";
            case "DONE" -> "Simulated financial action executed after consent";
            case "SKIPPED" -> "No financial action executed";
            default -> "Nothing executes without consent";
        };
        stages.add(new PipelineStage("ACT", "Act", actState,
                t.at("ACTION_EXECUTED", "ACTION_BLOCKED", "ACTION_FAILED", "NO_ACTION_EXECUTED"), actDetail));
        stages.add(new PipelineStage("AUDIT", "Audit", trail.isEmpty() ? "PENDING" : "DONE",
                trail.isEmpty() ? null : Duration.between(first, trail.get(trail.size() - 1).getCreatedAt()).toMillis(),
                trail.size() + " audit records"));

        String aiSource = gen.map(a -> a.getDetail().replaceFirst("^source=(\\S+).*$", "$1")).orElse(null);
        List<PipelineAgent> agents = List.of(
                new PipelineAgent("Financial Context Agent", "Assembles profile and bureau data", "DETERMINISTIC",
                        "ContextEnrichmentService", ctx.isPresent() ? (degraded ? "DEGRADED" : "DONE") : "PENDING",
                        ctx.map(AuditEventEntity::getDetail).orElse("-")),
                new PipelineAgent("Financial Vulnerability Agent", "Flags financial-pressure signals (illustrative, rule-based)",
                        "DETERMINISTIC", "FinancialSignalService", t.has("SIGNALS_DETECTED") ? "DONE" : "PENDING",
                        t.find("SIGNALS_DETECTED").map(AuditEventEntity::getDetail).orElse("-")),
                new PipelineAgent("Alternative Discovery Agent", "Finds eligible options within policy limits",
                        "DETERMINISTIC", "DecisionEngine + OptionRules", t.has("RULES_EVALUATED") ? "DONE" : "PENDING",
                        t.find("RULES_EVALUATED").map(AuditEventEntity::getDetail).orElse("-")),
                new PipelineAgent("Financial Coach Agent", "Explains and ranks the eligible options; cannot change amounts or act",
                        fallback ? "DETERMINISTIC_FALLBACK" : "AI", "RecommendationAgent", gen.isPresent() ? (fallback ? "DEGRADED" : "DONE") : "PENDING",
                        gen.map(a -> a.getDetail()).orElse("-")));

        List<ContextSource> considered = List.of(
                new ContextSource("Liquidity event", "EVENT", "OK", ev.getEventType().label() + " from " + ev.getSource()),
                new ContextSource("Customer profile", "MOCK_PROFILE_API", ctx.isPresent() ? "OK" : "PENDING", "Income, balances, savings, risk profile"),
                new ContextSource("Credit bureau", "MOCK_CREDIT_BUREAU", ctx.isPresent() ? (degraded ? "CACHED" : "OK") : "PENDING",
                        degraded ? "Unavailable: using last known values" : "Card balance, rate, credit score"),
                new ContextSource("Financial signals", "RULE_BASED", t.has("SIGNALS_DETECTED") ? "OK" : "PENDING", "Illustrative demo signals"),
                new ContextSource("Policy rules", "RULE_ENGINE", t.has("RULES_EVALUATED") ? "OK" : "PENDING",
                        "Eligibility, limits, account buffer, consent"));

        long ms = t.at("RECOMMENDATION_PUBLISHED") == null ? -1 : t.at("RECOMMENDATION_PUBLISHED");
        return new PipelineView(eventId, ev.getCorrelationId(), ev.getCustomerId(), ev.getEventType().name(), ev.getEventType().label(),
                ev.getAmount(), ev.getSource(), ev.getStatus().name(), props.latencyTargetMs(), ms, stages, agents, considered, PRINCIPLE);
    }

    private PipelineStage stage(String key, String label, Lookup t, String action, String doneState, String doneDetail) {
        Optional<AuditEventEntity> e = t.find(action);
        return new PipelineStage(key, label, e.isPresent() ? doneState : "PENDING", t.at(action),
                e.isPresent() ? doneDetail : "Not reached yet");
    }

    private record Lookup(List<AuditEventEntity> trail, Instant first) {
        Optional<AuditEventEntity> find(String action) {
            return trail.stream().filter(a -> a.getAction().equals(action)).findFirst();
        }

        boolean has(String action) {
            return find(action).isPresent();
        }

        Long at(String... actions) {
            long best = -1;
            for (String a : actions) {
                for (AuditEventEntity e : trail) {
                    if (e.getAction().equals(a)) best = Math.max(best, Duration.between(first, e.getCreatedAt()).toMillis());
                }
            }
            return best < 0 ? null : best;
        }
    }
}