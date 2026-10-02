package com.catalyst.api;

import com.catalyst.api.Dtos.*;
import com.catalyst.application.*;
import com.catalyst.domain.LiquidityEventType;
import com.catalyst.domain.LiquidityEventEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * REST surface. Nothing here is reachable by the AI agent; it has no client for these endpoints.
 */
public final class Controllers {
    private Controllers() {
    }

    @RestController
    @RequestMapping("/api/demo")
    @Tag(name = "Demo")
    public static class DemoController {
        private final LiquidityEventService ingestion;
        private final DemoService demo;

        public DemoController(LiquidityEventService ingestion, DemoService demo) {
            this.ingestion = ingestion;
            this.demo = demo;
        }

        @Operation(summary = "Simulate an inbound liquidity event (idempotent on idempotencyKey)")
        @PostMapping("/liquidity-events")
        public ResponseEntity<IngestResponse> simulate(@Valid @RequestBody LiquidityEventRequest req) {
            var result = ingestion.ingest(new LiquidityEventService.IngestCommand(req.scenario(),
                    req.customerId() == null ? "CUST-1001" : req.customerId(),
                    req.amount() == null ? req.scenario().defaultAmount() : req.amount(),
                    req.idempotencyKey(), req.memo(), MDC.get("correlationId")));
            LiquidityEventEntity e = result.event();
            String msg = result.duplicate() ? "Duplicate event detected. No duplicate financial action executed."
                    : "Event accepted and published for real-time decisioning.";
            return ResponseEntity.status(result.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                    .body(new IngestResponse(e.getId(), e.getCorrelationId(), e.getStatus().name(), result.duplicate(), msg));
        }

        @Operation(summary = "Re-deliver an already published event (simulates broker at-least-once delivery)")
        @PostMapping("/liquidity-events/{id}/replay")
        @ResponseStatus(HttpStatus.ACCEPTED)
        public void replay(@PathVariable String id) {
            ingestion.replay(id);
        }

        @GetMapping("/scenarios")
        public List<ScenarioView> scenarios() {
            return Arrays.stream(LiquidityEventType.values())
                    .map(t -> new ScenarioView(t, t.label(), t.defaultAmount(), t.defaultSource())).toList();
        }

        @GetMapping("/status")
        public DemoStatus status() {
            return demo.status();
        }

        @PostMapping("/toggles")
        public DemoStatus toggles(@RequestBody TogglesRequest r) {
            demo.toggles(r.creditBureauDown(), r.aiDown(), r.complianceBlock());
            return demo.status();
        }

        @PostMapping("/reset")
        public DemoStatus reset() {
            demo.reset();
            return demo.status();
        }
    }

    @RestController
    @RequestMapping("/api")
    @Tag(name = "Customer journey")
    public static class JourneyController {
        private final QueryService query;
        private final RecommendationWorkflowService workflow;
        private final ActionExecutionService execution;
        private final PipelineService pipeline;

        public JourneyController(QueryService query, RecommendationWorkflowService workflow, ActionExecutionService execution,
                                 PipelineService pipeline) {
            this.query = query;
            this.workflow = workflow;
            this.execution = execution;
            this.pipeline = pipeline;
        }

        @GetMapping("/liquidity-events/{id}")
        public EventView event(@PathVariable String id) {
            return query.event(id);
        }

        @GetMapping("/liquidity-events")
        public List<EventView> recent(@RequestParam(defaultValue = "CUST-1001") String customerId) {
            return query.recentEvents(customerId);
        }

        @GetMapping("/recommendations/{id}")
        public RecommendationView recommendation(@PathVariable String id) {
            return query.recommendation(id);
        }

        @PostMapping("/recommendations/{id}/viewed")
        public RecommendationView viewed(@PathVariable String id) {
            workflow.markViewed(id);
            return query.recommendation(id);
        }

        @Operation(summary = "Customer explicitly approves selected actions. Requires confirmed=true.")
        @PostMapping("/recommendations/{id}/approve")
        public RecommendationView approve(@PathVariable String id, @RequestBody ApproveRequest req) {
            workflow.approve(id, req.actionIds(), req.confirmed(), req.amounts());
            return query.recommendation(id);
        }

        @Operation(summary = "Customer consent: accept, or modify (reduce) amounts, then approve. Requires confirmed=true.")
        @PostMapping("/recommendations/{id}/consent")
        public RecommendationView consent(@PathVariable String id, @RequestBody ApproveRequest req) {
            workflow.approve(id, req.actionIds(), req.confirmed(), req.amounts());
            return query.recommendation(id);
        }

        @PostMapping("/recommendations/{id}/decline")
        public RecommendationView decline(@PathVariable String id) {
            workflow.decline(id);
            return query.recommendation(id);
        }

        @GetMapping("/actions/{id}")
        public ActionView action(@PathVariable String id) {
            return query.action(id);
        }

        @Operation(summary = "Approve a single action (customer consent)")
        @PostMapping("/actions/{id}/approve")
        public ActionView approveAction(@PathVariable String id, @RequestBody ActionApproveRequest req) {
            workflow.approveAction(id, req.confirmed());
            return query.action(id);
        }

        @Operation(summary = "Execute an approved action; rejected (403) without valid consent")
        @PostMapping("/actions/{id}/execute")
        public ActionView executeAction(@PathVariable String id) {
            execution.execute(id);
            return query.action(id);
        }

        @GetMapping("/customers/{id}")
        public CustomerView customer(@PathVariable String id) {
            return query.customer(id);
        }

        @Operation(summary = "Customer financial context with illustrative, rule-based demo signals")
        @GetMapping("/customers/{id}/financial-context")
        public FinancialContextView financialContext(@PathVariable String id) {
            query.customer(id);
            return pipeline.financialContext(id);
        }

        @Operation(summary = "Pipeline stages, agents and context considered for an event (derived from the audit trail)")
        @GetMapping("/pipeline/{eventId}")
        public PipelineView pipeline(@PathVariable String eventId) {
            return pipeline.pipeline(eventId);
        }

        @GetMapping("/audit/{eventId}")
        public TimelineView audit(@PathVariable String eventId) {
            return query.timeline(eventId);
        }
    }
}
