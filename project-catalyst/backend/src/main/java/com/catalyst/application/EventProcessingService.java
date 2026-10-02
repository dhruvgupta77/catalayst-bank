package com.catalyst.application;

import com.catalyst.audit.AuditService;
import com.catalyst.customer.ContextEnrichmentService;
import com.catalyst.decision.*;
import com.catalyst.demo.CatalystMetrics;
import com.catalyst.domain.*;
import com.catalyst.event.*;
import com.catalyst.infrastructure.persistence.Repositories.*;
import com.catalyst.recommendation.AiRecommendation;
import com.catalyst.recommendation.RecommendationOutcome;
import com.catalyst.recommendation.RecommendationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Consumer side of the pipeline: enrich -> decide -> recommend -> publish. Never executes anything.
 */
@Service
public class EventProcessingService {
    private static final Logger log = LoggerFactory.getLogger(EventProcessingService.class);

    public record Envelope(AiRecommendation narrative, String fallbackReason, List<String> contextNotes,
                           BigDecimal keptInAccount, BigDecimal allocatable, List<FinancialSignal> signals) {
    }

    private final LiquidityEventRepository events;
    private final FinancialContextRepository contexts;
    private final RecommendationRepository recommendations;
    private final FinancialActionRepository actions;
    private final ContextEnrichmentService enrichment;
    private final FinancialSignalService signalService;
    private final DecisionEngine engine;
    private final ProductCatalog catalog;
    private final RecommendationService recommendationService;
    private final AuditService audit;
    private final EventBus bus;
    private final CatalystMetrics metrics;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public EventProcessingService(LiquidityEventRepository events, FinancialContextRepository contexts,
                                  RecommendationRepository recommendations, FinancialActionRepository actions,
                                  ContextEnrichmentService enrichment, FinancialSignalService signalService,
                                  DecisionEngine engine, ProductCatalog catalog,
                                  RecommendationService recommendationService, AuditService audit, EventBus bus,
                                  CatalystMetrics metrics, ObjectMapper mapper, TransactionTemplate tx) {
        this.events = events;
        this.contexts = contexts;
        this.recommendations = recommendations;
        this.actions = actions;
        this.enrichment = enrichment;
        this.signalService = signalService;
        this.engine = engine;
        this.catalog = catalog;
        this.recommendationService = recommendationService;
        this.audit = audit;
        this.bus = bus;
        this.metrics = metrics;
        this.mapper = mapper;
        this.tx = tx;
    }

    public void process(LiquidityEventMessage msg) {
        MDC.put("correlationId", msg.correlationId());
        MDC.put("eventId", msg.eventId());
        try {
            // Atomic claim = idempotency guard: only one consumer delivery can move RECEIVED -> PROCESSING.
            if (events.transition(msg.eventId(), EventStatus.RECEIVED, EventStatus.PROCESSING) == 0) {
                audit.record(msg.eventId(), msg.customerId(), msg.correlationId(), "DUPLICATE_EVENT_DETECTED", "SYSTEM",
                        "IGNORED", "Duplicate delivery ignored; no duplicate financial action executed");
                log.info("Duplicate delivery ignored");
                return;
            }
            Instant started = Instant.now();
            try {
                run(msg);
                metrics.processing(Duration.between(started, Instant.now()));
                metrics.inc("liquidity.events.processed");
            } catch (RuntimeException e) {
                log.error("Processing failed: {}", e.toString());
                events.transition(msg.eventId(), EventStatus.PROCESSING, EventStatus.FAILED);
                audit.record(msg.eventId(), msg.customerId(), msg.correlationId(), "PROCESSING_FAILED", "SYSTEM", "FAILURE",
                        "Processing failed: " + e.getClass().getSimpleName());
            }
        } finally {
            MDC.remove("correlationId");
            MDC.remove("eventId");
        }
    }

    private void run(LiquidityEventMessage msg) {
        LiquidityEvent event = new LiquidityEvent(msg.eventId(), LiquidityEventType.valueOf(msg.eventType()),
                msg.customerId(), msg.amount(), msg.currency(), msg.timestamp(), msg.source());

        FinancialContext ctx = enrichment.enrich(event);
        FinancialContextEntity ce = new FinancialContextEntity();
        ce.setEventId(event.eventId());
        ce.setCustomerId(event.customerId());
        ce.setContextJson(json(minimised(ctx)));
        ce.setReducedContext(ctx.reducedContext());
        ce.setCreatedAt(Instant.now());
        contexts.save(ce);
        audit.record(event.eventId(), event.customerId(), msg.correlationId(), "CONTEXT_ENRICHED", "SYSTEM",
                ctx.reducedContext() ? "DEGRADED" : "OK",
                ctx.reducedContext() ? String.join("; ", ctx.notes()) : "Customer profile and credit bureau data retrieved");
        bus.publish(Topics.CUSTOMER_CONTEXT, event.customerId(), stage(msg, "CONTEXT_ENRICHED", event.eventId(),
                Map.of("reducedContext", ctx.reducedContext())));

        List<FinancialSignal> signals = signalService.assessBeforeEvent(ctx, event.amount());
        audit.record(event.eventId(), event.customerId(), msg.correlationId(), "SIGNALS_DETECTED", "SIGNAL_ENGINE", "OK",
                "Illustrative demo signals (rule-based): " + (signals.isEmpty() ? "none"
                        : signals.stream().map(FinancialSignal::id).collect(Collectors.joining(", "))));

        DecisionResult decision = engine.decide(event, ctx, catalog);
        long passed = decision.ruleOutcomes().stream().filter(RuleOutcome::passed).count();
        audit.record(event.eventId(), event.customerId(), msg.correlationId(), "RULES_EVALUATED", "RULE_ENGINE", "OK",
                passed + "/" + decision.ruleOutcomes().size() + " rules passed; " + decision.recommendedActions().size()
                        + " eligible options: " + decision.ruleOutcomes().stream().filter(RuleOutcome::passed)
                        .map(RuleOutcome::ruleId).collect(Collectors.joining(", ")));

        Instant recoStart = Instant.now();
        RecommendationOutcome outcome = recommendationService.recommend(event, ctx, decision);
        long recoMs = Duration.between(recoStart, Instant.now()).toMillis();
        metrics.recommendation(Duration.ofMillis(recoMs));

        String recoId = "REC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        Envelope env = new Envelope(outcome.narrative(), outcome.fallbackReason(), ctx.notes(),
                decision.keptInAccount(), decision.allocatable(), signals);
        tx.executeWithoutResult(s -> {
            RecommendationEntity r = new RecommendationEntity();
            r.setId(recoId);
            r.setEventId(event.eventId());
            r.setCustomerId(event.customerId());
            r.setStatus(RecommendationStatus.GENERATED);
            r.setSource(outcome.source());
            r.setNarrativeJson(json(env));
            r.setRulesJson(json(decision.ruleOutcomes()));
            r.setReducedContext(ctx.reducedContext());
            r.setGenerationMs(recoMs);
            r.setCreatedAt(Instant.now());
            recommendations.saveAndFlush(r);
            for (ActionProposal p : outcome.orderedActions()) {
                FinancialActionEntity a = new FinancialActionEntity();
                a.setId("ACT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
                a.setRecommendationId(recoId);
                a.setEventId(event.eventId());
                a.setCustomerId(event.customerId());
                a.setActionType(p.type());
                a.setAmount(p.amount());
                a.setProductId(p.productId());
                a.setProductName(p.productName());
                a.setPriority(p.priority());
                a.setReason(p.reason());
                a.setImpact(p.impact());
                a.setStatus(ActionStatus.PROPOSED);
                a.setCreatedAt(Instant.now());
                actions.save(a);
            }
            LiquidityEventEntity ev = events.findById(event.eventId()).orElseThrow();
            ev.setRecommendationId(recoId);
            ev.setStatus(EventStatus.RECOMMENDED);
            events.save(ev);
        });
        metrics.inc("recommendations.generated");
        audit.record(event.eventId(), event.customerId(), msg.correlationId(), "RECOMMENDATION_GENERATED",
                "RULES_FALLBACK".equals(outcome.source()) ? "RULE_ENGINE" : "AI_AGENT", "OK",
                "source=" + outcome.source() + (outcome.fallbackReason() == null ? "" : " fallback=" + outcome.fallbackReason()));
        audit.record(event.eventId(), event.customerId(), msg.correlationId(), "RECOMMENDATION_PUBLISHED", "SYSTEM", "OK",
                "Customer experience updated; awaiting customer review");
        bus.publish(Topics.RECOMMENDATIONS, event.customerId(), stage(msg, "RECOMMENDATION_GENERATED", recoId,
                Map.of("source", outcome.source(), "options", outcome.orderedActions().size())));
    }

    /**
     * Snapshot stored for audit: no names or account identifiers.
     */
    private Map<String, Object> minimised(FinancialContext c) {
        return Map.of("riskProfile", c.riskProfile(), "currentBalance", c.currentBalance(),
                "emergencySavings", c.emergencySavings(), "emergencyTarget", c.emergencyTarget(),
                "investmentBalance", c.investmentBalance(), "reducedContext", c.reducedContext(),
                "creditCardBalance", c.creditCardBalance() == null ? "unavailable" : c.creditCardBalance(),
                "creditCardRate", c.creditCardRate() == null ? "unavailable" : c.creditCardRate());
    }

    private StageMessage stage(LiquidityEventMessage m, String stage, String ref, Map<String, Object> data) {
        return new StageMessage(m.eventId(), m.correlationId(), m.customerId(), stage, ref, data);
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
