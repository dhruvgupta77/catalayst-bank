package com.catalyst.application;

import com.catalyst.api.Dtos.*;
import com.catalyst.api.NotFoundException;
import com.catalyst.application.EventProcessingService.Envelope;
import com.catalyst.decision.RuleOutcome;
import com.catalyst.demo.CatalystProperties;
import com.catalyst.domain.*;
import com.catalyst.audit.AuditService;
import com.catalyst.infrastructure.persistence.Repositories.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class QueryService {
    private final LiquidityEventRepository events;
    private final RecommendationRepository recommendations;
    private final FinancialActionRepository actions;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final CatalystProperties props;
    private final ObjectMapper mapper;

    public QueryService(LiquidityEventRepository events, RecommendationRepository recommendations,
                        FinancialActionRepository actions, CustomerRepository customers, AccountRepository accounts,
                        AuditService audit, CatalystProperties props, ObjectMapper mapper) {
        this.events = events;
        this.recommendations = recommendations;
        this.actions = actions;
        this.customers = customers;
        this.accounts = accounts;
        this.audit = audit;
        this.props = props;
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public EventView event(String id) {
        return toView(events.findById(id).orElseThrow(() -> new NotFoundException("Event " + id + " not found")));
    }

    public List<EventView> recentEvents(String customerId) {
        return events.findTop10ByCustomerIdOrderByReceivedAtDesc(customerId).stream().map(this::toView).toList();
    }

    public RecommendationView recommendation(String id) {
        RecommendationEntity r = recommendations.findById(id)
                .orElseThrow(() -> new NotFoundException("Recommendation " + id + " not found"));
        try {
            Envelope env = mapper.readValue(r.getNarrativeJson(), Envelope.class);
            List<RuleOutcome> rules = mapper.readValue(r.getRulesJson(), new TypeReference<>() {
            });
            List<ActionView> acts = actions.findByRecommendationIdOrderByPriorityAsc(id).stream().map(QueryService::toView).toList();
            return new RecommendationView(r.getId(), r.getEventId(), r.getStatus().name(), r.getSource(), env.fallbackReason(),
                    env.narrative(), acts, rules, r.isReducedContext(), env.contextNotes(), env.keptInAccount(),
                    env.allocatable(), event(r.getEventId()), r.getGenerationMs(), r.getCreatedAt(),
                    env.signals() == null ? List.of() : env.signals());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Corrupt recommendation payload", e);
        }
    }

    public ActionView action(String id) {
        return toView(actions.findById(id).orElseThrow(() -> new NotFoundException("Action not found")));
    }

    public CustomerView customer(String id) {
        CustomerEntity c = customers.findById(id).orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
        List<AccountEntity> accs = accounts.findByCustomerId(id);
        BigDecimal savings = bal(accs, AccountType.SAVINGS);
        BigDecimal card = bal(accs, AccountType.CREDIT_CARD);
        BigDecimal loan = bal(accs, AccountType.PERSONAL_LOAN);
        int coverage = savings.multiply(BigDecimal.valueOf(100)).divide(c.getEmergencyFundTarget(), 0, RoundingMode.HALF_UP).intValue();
        return new CustomerView(c.getId(), c.getName(), c.getAge(), c.getMonthlySalary(),
                accs.stream().map(a -> new AccountView(a.getType().name(), a.getName(), a.getBalance(), a.getInterestRate())).toList(),
                new HealthIndicators(coverage, card, card.add(loan), c.getCreditScore(), c.getRiskProfile().name()));
    }

    public TimelineView timeline(String eventId) {
        var trail = audit.trail(eventId);
        if (trail.isEmpty()) throw new NotFoundException("No audit trail for event " + eventId);
        var first = trail.get(0).getCreatedAt();
        List<AuditEntry> entries = trail.stream().map(a -> new AuditEntry(a.getSeq(), a.getAction(), a.getActor(), a.getResult(),
                a.getDetail(), a.getCorrelationId(), a.getCustomerId(), a.getCreatedAt(),
                java.time.Duration.between(first, a.getCreatedAt()).toMillis())).toList();
        long ms = entries.stream().filter(e -> e.action().equals("RECOMMENDATION_PUBLISHED")).mapToLong(AuditEntry::offsetMs)
                .findFirst().orElse(-1);
        return new TimelineView(eventId, props.latencyTargetMs(), ms, ms >= 0 && ms <= props.latencyTargetMs(),
                "Demo Processing Timeline (local demo run, not a production latency claim)", entries);
    }

    private EventView toView(LiquidityEventEntity e) {
        return new EventView(e.getId(), e.getCorrelationId(), e.getEventType(), e.getEventType().label(), e.getCustomerId(),
                e.getAmount(), e.getCurrency(), e.getSource(), e.getStatus().name(), e.getReceivedAt(), e.getRecommendationId());
    }

    private static ActionView toView(FinancialActionEntity a) {
        return new ActionView(a.getId(), a.getActionType().name(), a.getAmount(), a.getPriority(), a.getReason(), a.getImpact(),
                a.getProductName(), a.getStatus().name(), a.getProgress(), a.getProgressNote(), a.getFailureReason());
    }

    private static BigDecimal bal(List<AccountEntity> a, AccountType t) {
        return a.stream().filter(x -> x.getType() == t).map(AccountEntity::getBalance).findFirst().orElse(BigDecimal.ZERO);
    }
}
