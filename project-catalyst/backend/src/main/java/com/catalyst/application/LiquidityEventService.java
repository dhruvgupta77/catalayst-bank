package com.catalyst.application;

import com.catalyst.api.NotFoundException;
import com.catalyst.audit.AuditService;
import com.catalyst.demo.CatalystMetrics;
import com.catalyst.domain.*;
import com.catalyst.event.EventBus;
import com.catalyst.event.LiquidityEventMessage;
import com.catalyst.event.Topics;
import com.catalyst.infrastructure.persistence.Repositories.AccountRepository;
import com.catalyst.infrastructure.persistence.Repositories.CustomerRepository;
import com.catalyst.infrastructure.persistence.Repositories.LiquidityEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class LiquidityEventService {
    private static final Logger log = LoggerFactory.getLogger(LiquidityEventService.class);

    public record IngestCommand(LiquidityEventType type, String customerId, BigDecimal amount, String idempotencyKey,
                                String memo, String correlationId) {
    }

    public record IngestResult(LiquidityEventEntity event, boolean duplicate) {
    }

    private final LiquidityEventRepository events;
    private final CustomerRepository customers;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final EventBus bus;
    private final CatalystMetrics metrics;
    private final TransactionTemplate tx;

    public LiquidityEventService(LiquidityEventRepository events, CustomerRepository customers, AccountRepository accounts,
                                 AuditService audit, EventBus bus, CatalystMetrics metrics, TransactionTemplate tx) {
        this.events = events;
        this.customers = customers;
        this.accounts = accounts;
        this.audit = audit;
        this.bus = bus;
        this.metrics = metrics;
        this.tx = tx;
    }

    /**
     * Persists the event (idempotently), credits the ledger, then publishes to the bus after commit.
     */
    public IngestResult ingest(IngestCommand cmd) {
        customers.findById(cmd.customerId()).orElseThrow(() -> new NotFoundException("Customer " + cmd.customerId() + " not found"));
        String key = cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank()
                ? "auto-" + UUID.randomUUID() : cmd.idempotencyKey();
        String correlationId = cmd.correlationId() != null ? cmd.correlationId() : "COR-" + shortId();

        var existing = events.findByIdempotencyKey(key);
        if (existing.isPresent()) return duplicate(existing.get(), correlationId);

        LiquidityEventEntity created;
        try {
            created = tx.execute(status -> {
                Instant now = Instant.now();
                LiquidityEventEntity e = new LiquidityEventEntity();
                e.setId("EVT-" + shortId());
                e.setIdempotencyKey(key);
                e.setCorrelationId(correlationId);
                e.setEventType(cmd.type());
                e.setCustomerId(cmd.customerId());
                e.setAmount(cmd.amount());
                e.setCurrency("ZAR");
                e.setSource(cmd.type().defaultSource());
                e.setMemo(cmd.memo());
                e.setStatus(EventStatus.RECEIVED);
                e.setOccurredAt(now);
                e.setReceivedAt(now);
                e = events.saveAndFlush(e);
                AccountEntity current = accounts.findByCustomerIdAndType(cmd.customerId(), AccountType.CURRENT).orElseThrow();
                accounts.credit(current.getId(), cmd.amount());
                audit.record(e.getId(), e.getCustomerId(), correlationId, "EVENT_RECEIVED", "SYSTEM", "OK",
                        cmd.type() + " inflow credited to current account");
                return e;
            });
        } catch (DataIntegrityViolationException race) {
            return duplicate(events.findByIdempotencyKey(key).orElseThrow(), correlationId);
        }

        metrics.inc("liquidity.events.received");
        MDC.put("eventId", created.getId());
        log.info("Liquidity event received type={} correlationId={}", created.getEventType(), correlationId);
        MDC.remove("eventId");
        publish(created);
        return new IngestResult(created, false);
    }

    /**
     * Re-delivers the same message (as an at-least-once broker could). Consumers must stay idempotent.
     */
    public void replay(String eventId) {
        publish(events.findById(eventId).orElseThrow(() -> new NotFoundException("Event " + eventId + " not found")));
    }

    private void publish(LiquidityEventEntity e) {
        bus.publish(Topics.LIQUIDITY_EVENTS, e.getCustomerId(), new LiquidityEventMessage(e.getId(), e.getCorrelationId(),
                e.getEventType().name(), e.getCustomerId(), e.getAmount(), e.getCurrency(), e.getOccurredAt(), e.getSource()));
    }

    private IngestResult duplicate(LiquidityEventEntity e, String correlationId) {
        audit.record(e.getId(), e.getCustomerId(), e.getCorrelationId(), "DUPLICATE_EVENT_DETECTED", "SYSTEM", "IGNORED",
                "Duplicate submission (same idempotency key); no duplicate financial action executed");
        return new IngestResult(e, true);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
