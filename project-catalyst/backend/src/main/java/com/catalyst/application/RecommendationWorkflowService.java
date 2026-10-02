package com.catalyst.application;

import com.catalyst.api.BadRequestException;
import com.catalyst.api.ConflictException;
import com.catalyst.api.NotFoundException;
import com.catalyst.api.PolicyViolationException;
import com.catalyst.decision.Product;
import com.catalyst.decision.ProductCatalog;
import com.catalyst.audit.AuditService;
import com.catalyst.consent.ConsentService;
import com.catalyst.demo.CatalystMetrics;
import com.catalyst.domain.*;
import com.catalyst.event.EventBus;
import com.catalyst.event.StageMessage;
import com.catalyst.event.Topics;
import com.catalyst.infrastructure.persistence.Repositories.FinancialActionRepository;
import com.catalyst.infrastructure.persistence.Repositories.LiquidityEventRepository;
import com.catalyst.infrastructure.persistence.Repositories.RecommendationRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Customer decisions: view, approve (explicit consent), decline. The only place consent is granted.
 */
@Service
public class RecommendationWorkflowService {
    private static final List<RecommendationStatus> OPEN = List.of(RecommendationStatus.GENERATED, RecommendationStatus.VIEWED);

    private final RecommendationRepository recommendations;
    private final FinancialActionRepository actions;
    private final ConsentService consent;
    private final AuditService audit;
    private final EventBus bus;
    private final CatalystMetrics metrics;
    private final LiquidityEventRepository eventRepo;
    private final ProductCatalog catalog;

    public RecommendationWorkflowService(RecommendationRepository recommendations, FinancialActionRepository actions,
                                         ConsentService consent, AuditService audit, EventBus bus,
                                         CatalystMetrics metrics, LiquidityEventRepository eventRepo, ProductCatalog catalog) {
        this.recommendations = recommendations;
        this.actions = actions;
        this.consent = consent;
        this.audit = audit;
        this.bus = bus;
        this.metrics = metrics;
        this.eventRepo = eventRepo;
        this.catalog = catalog;
    }

    public void markViewed(String recoId) {
        RecommendationEntity r = load(recoId);
        if (r.getViewedAt() != null) return;
        r.setViewedAt(Instant.now());
        if (r.getStatus() == RecommendationStatus.GENERATED) r.setStatus(RecommendationStatus.VIEWED);
        recommendations.save(r);
        audit.record(r.getEventId(), r.getCustomerId(), corr(r), "CUSTOMER_VIEWED", "CUSTOMER", "OK",
                "Customer opened the recommendation");
    }

    /**
     * Approve the chosen actions of a recommendation; the remaining actions are cancelled.
     */
    public void approve(String recoId, Set<String> actionIds, boolean confirmed) {
        approve(recoId, actionIds, confirmed, null);
    }

    /**
     * Approve, optionally with reduced amounts ("modify"). A customer may lower an amount but never exceed what the
     * rules approved; anything else is blocked by policy and audited.
     */
    public void approve(String recoId, Set<String> actionIds, boolean confirmed, Map<String, BigDecimal> amounts) {
        if (!confirmed) throw new BadRequestException("Explicit confirmation is required to approve");
        if (actionIds == null || actionIds.isEmpty())
            throw new BadRequestException("Select at least one action to approve");
        RecommendationEntity r = load(recoId);
        List<FinancialActionEntity> all = actions.findByRecommendationIdOrderByPriorityAsc(recoId);
        Set<String> known = all.stream().map(FinancialActionEntity::getId).collect(Collectors.toSet());
        if (!known.containsAll(actionIds)) throw new BadRequestException("Unknown action for this recommendation");
        if (r.getStatus() == RecommendationStatus.DECLINED) throw new ConflictException("Recommendation was declined");
        if (r.getStatus() == RecommendationStatus.APPROVED) return; // idempotent re-submit

        Map<String, BigDecimal> changes = validateModifications(r, all, actionIds, amounts);

        if (recommendations.decide(recoId, OPEN, RecommendationStatus.APPROVED) == 0) return;
        metrics.inc("recommendations.accepted");
        for (FinancialActionEntity a : all) {
            BigDecimal newAmount = changes.get(a.getId());
            if (newAmount == null) continue;
            BigDecimal old = a.getAmount();
            a.setAmount(newAmount);
            actions.save(a);
            audit.record(r.getEventId(), r.getCustomerId(), corr(r), "CUSTOMER_MODIFIED", ConsentService.CUSTOMER, "OK",
                    "Customer changed " + a.getActionType() + " from " + rand(old) + " to " + rand(newAmount));
        }
        for (FinancialActionEntity a : all) {
            if (!actionIds.contains(a.getId()) && actions.transition(a.getId(), ActionStatus.PROPOSED, ActionStatus.CANCELLED) == 1) {
                audit.record(r.getEventId(), r.getCustomerId(), corr(r), "ACTION_SKIPPED", "CUSTOMER", "OK",
                        "Customer chose not to proceed with " + a.getActionType());
            }
        }
        all.stream().filter(a -> actionIds.contains(a.getId())).forEach(a -> grantAndPublish(r, a, "CONFIRMATION_SCREEN"));
    }

    /**
     * Approve one action (POST /api/actions/{id}/approve).
     */
    public void approveAction(String actionId, boolean confirmed) {
        if (!confirmed) throw new BadRequestException("Explicit confirmation is required to approve");
        FinancialActionEntity a = actions.findById(actionId).orElseThrow(() -> new NotFoundException("Action not found"));
        RecommendationEntity r = load(a.getRecommendationId());
        if (r.getStatus() == RecommendationStatus.DECLINED || a.getStatus() == ActionStatus.CANCELLED) {
            throw new ConflictException("Action is no longer available");
        }
        if (a.getStatus() != ActionStatus.PROPOSED) return; // idempotent
        if (OPEN.contains(r.getStatus())) {
            recommendations.decide(r.getId(), OPEN, RecommendationStatus.APPROVED);
            metrics.inc("recommendations.accepted");
        }
        grantAndPublish(r, a, "ACTION_ENDPOINT");
    }

    public void decline(String recoId) {
        RecommendationEntity r = load(recoId);
        if (r.getStatus() == RecommendationStatus.DECLINED) return;
        if (recommendations.decide(recoId, OPEN, RecommendationStatus.DECLINED) == 0) {
            throw new ConflictException("Recommendation was already approved");
        }
        actions.findByRecommendationIdOrderByPriorityAsc(recoId)
                .forEach(a -> actions.transition(a.getId(), ActionStatus.PROPOSED, ActionStatus.CANCELLED));
        audit.record(r.getEventId(), r.getCustomerId(), corr(r), "CUSTOMER_DECLINED", "CUSTOMER", "OK",
                "Recommendation declined; no financial action executed");
        audit.record(r.getEventId(), r.getCustomerId(), corr(r), "NO_ACTION_EXECUTED", "SYSTEM", "OK",
                "No financial action was executed");
    }

    private Map<String, BigDecimal> validateModifications(RecommendationEntity r, List<FinancialActionEntity> all,
                                                          Set<String> actionIds, Map<String, BigDecimal> amounts) {
        Map<String, BigDecimal> changes = new LinkedHashMap<>();
        if (amounts == null || amounts.isEmpty()) return changes;
        for (Map.Entry<String, BigDecimal> en : amounts.entrySet()) {
            FinancialActionEntity a = all.stream().filter(x -> x.getId().equals(en.getKey())).findFirst()
                    .orElseThrow(() -> new BadRequestException("Unknown action for this recommendation"));
            if (!actionIds.contains(a.getId())) throw new BadRequestException("Amount supplied for an action that is not selected");
            if (en.getValue() == null || en.getValue().signum() <= 0) throw new BadRequestException("Amount must be greater than zero");
            BigDecimal amt = en.getValue().setScale(2, RoundingMode.HALF_UP);
            if (amt.compareTo(a.getAmount()) == 0) continue;
            if (amt.compareTo(a.getAmount()) > 0) {
                throw blocked(r, "POLICY_LIMIT", a, "Requested " + rand(amt) + " exceeds the rule-approved limit of "
                        + rand(a.getAmount()) + " for " + a.getActionType());
            }
            Product p = catalog.byId(a.getProductId()).orElse(null);
            if (p != null && amt.compareTo(p.minAmount()) < 0) {
                throw blocked(r, "PRODUCT_MINIMUM", a, rand(amt) + " is below the " + rand(p.minAmount()) + " minimum for " + p.name());
            }
            changes.put(a.getId(), amt);
        }
        return changes;
    }

    private PolicyViolationException blocked(RecommendationEntity r, String rule, FinancialActionEntity a, String message) {
        audit.record(r.getEventId(), r.getCustomerId(), corr(r), "POLICY_BLOCKED", "RULE_ENGINE", "DENIED", rule + ": " + message);
        return new PolicyViolationException("Blocked by policy (" + rule + "): " + message);
    }

    private static String rand(BigDecimal v) {
        return String.format(Locale.US, "R%,.0f", v.setScale(0, RoundingMode.HALF_UP));
    }

    private void grantAndPublish(RecommendationEntity r, FinancialActionEntity a, String channel) {
        if (actions.transition(a.getId(), ActionStatus.PROPOSED, ActionStatus.APPROVED) == 0) return;
        consent.grant(a, channel);
        audit.record(r.getEventId(), r.getCustomerId(), corr(r), "CUSTOMER_APPROVED", ConsentService.CUSTOMER, "OK",
                "Consent recorded for " + a.getActionType() + " (" + a.getId() + ")");
        bus.publish(Topics.FINANCIAL_ACTIONS, a.getCustomerId(), new StageMessage(r.getEventId(), corr(r),
                a.getCustomerId(), "ACTION_APPROVED", a.getId(), Map.of("type", a.getActionType().name())));
    }

    private RecommendationEntity load(String id) {
        return recommendations.findById(id).orElseThrow(() -> new NotFoundException("Recommendation " + id + " not found"));
    }

    private String corr(RecommendationEntity r) {
        return eventRepo.findById(r.getEventId()).map(LiquidityEventEntity::getCorrelationId).orElse("COR-UNKNOWN");
    }
}
