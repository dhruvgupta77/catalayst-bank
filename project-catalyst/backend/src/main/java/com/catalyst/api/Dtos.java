package com.catalyst.api;

import com.catalyst.decision.FinancialSignal;
import com.catalyst.decision.RuleOutcome;
import com.catalyst.domain.LiquidityEventType;
import com.catalyst.recommendation.AiRecommendation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Dtos {
    private Dtos() {
    }

    public record LiquidityEventRequest(
            @NotNull LiquidityEventType scenario,
            @Pattern(regexp = "^CUST-\\d{4,10}$") String customerId,
            @DecimalMin("1.00") @DecimalMax("10000000.00") BigDecimal amount,
            @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9._:-]*$") String idempotencyKey,
            @Size(max = 200) String memo) {
    }

    public record IngestResponse(String eventId, String correlationId, String status, boolean duplicate,
                                 String message) {
    }

    public record ApproveRequest(Set<String> actionIds, boolean confirmed, Map<String, BigDecimal> amounts) {
        public ApproveRequest(Set<String> actionIds, boolean confirmed) {
            this(actionIds, confirmed, null);
        }
    }

    public record ActionApproveRequest(boolean confirmed) {
    }

    public record TogglesRequest(Boolean creditBureauDown, Boolean aiDown, Boolean complianceBlock) {
    }

    public record ScenarioView(LiquidityEventType type, String label, BigDecimal amount, String source) {
    }

    public record EventView(String id, String correlationId, LiquidityEventType type, String label, String customerId,
                            BigDecimal amount, String currency, String source, String status, Instant receivedAt,
                            String recommendationId) {
    }

    public record ActionView(String id, String type, BigDecimal amount, int priority, String reason, String impact,
                             String productName, String status, int progress, String progressNote,
                             String failureReason) {
    }

    public record RecommendationView(String id, String eventId, String status, String source, String fallbackReason,
                                     AiRecommendation narrative, List<ActionView> actions, List<RuleOutcome> rules,
                                     boolean reducedContext, List<String> contextNotes, BigDecimal keptInAccount,
                                     BigDecimal allocatable, EventView event, long generationMs, Instant createdAt,
                                     List<FinancialSignal> signals) {
    }

    public record AccountView(String type, String name, BigDecimal balance, BigDecimal interestRate) {
    }

    public record HealthIndicators(int emergencyCoveragePercent, BigDecimal highInterestDebt, BigDecimal totalDebt,
                                   int creditScore, String riskProfile) {
    }

    public record CustomerView(String id, String name, int age, BigDecimal monthlySalary, List<AccountView> accounts,
                               HealthIndicators health) {
    }

    public record AuditEntry(long seq, String action, String actor, String result, String detail, String correlationId,
                             String customerId, Instant timestamp, long offsetMs) {
    }

    public record TimelineView(String eventId, long latencyTargetMs, long msToRecommendation, boolean withinTarget,
                               String label, List<AuditEntry> entries) {
    }

    public record DemoStatus(String bus, String aiMode, String aiSource, List<String> profiles,
                             boolean creditBureauDown,
                             boolean aiDown, boolean complianceBlock, long latencyTargetMs, Map<String, Double> metrics, String principle) {
    }

    public record ContextSource(String name, String kind, String status, String detail) {
    }

    public record FinancialContextView(String label, String customerId, String customerName, BigDecimal monthlySalary,
                                       BigDecimal currentBalance, BigDecimal emergencySavings, BigDecimal emergencyTarget,
                                       BigDecimal investmentBalance, BigDecimal creditCardBalance, BigDecimal creditCardRate,
                                       BigDecimal loanBalance, BigDecimal monthlyLoanPayment, String riskProfile,
                                       boolean reducedContext, List<String> notes, List<FinancialSignal> signals,
                                       List<ContextSource> sources, String disclaimer) {
    }

    public record PipelineStage(String key, String label, String state, Long atMs, String detail) {
    }

    public record PipelineAgent(String name, String role, String kind, String implementation, String status,
                                String detail) {
    }

    public record PipelineView(String eventId, String correlationId, String customerId, String eventType, String eventLabel,
                               BigDecimal amount, String source, String status, long latencyTargetMs, long msToRecommendation,
                               List<PipelineStage> stages, List<PipelineAgent> agents, List<ContextSource> contextConsidered,
                               String principle) {
    }

    public record ApiError(int status, String error, String message, Instant timestamp) {
    }
}
