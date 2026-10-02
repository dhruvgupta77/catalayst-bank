package com.catalyst.audit;

import com.catalyst.domain.AuditEventEntity;
import com.catalyst.event.EventBus;
import com.catalyst.event.StageMessage;
import com.catalyst.event.Topics;
import com.catalyst.infrastructure.persistence.Repositories.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Append-only audit trail. Details must never contain names, account numbers or other PII.
 */
@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repo;
    private final EventBus bus;

    public AuditService(AuditEventRepository repo, EventBus bus) {
        this.repo = repo;
        this.bus = bus;
    }

    public AuditEventEntity record(String eventId, String customerId, String correlationId, String action,
                                   String actor, String result, String detail) {
        AuditEventEntity e = new AuditEventEntity();
        e.setEventId(eventId);
        e.setCustomerId(customerId);
        e.setCorrelationId(correlationId);
        e.setAction(action);
        e.setActor(actor);
        e.setResult(result);
        e.setDetail(detail);
        e.setCreatedAt(Instant.now());
        e = repo.save(e);
        log.info("audit action={} actor={} result={} eventId={}", action, actor, result, eventId);
        bus.publish(Topics.AUDIT_EVENTS, eventId,
                new StageMessage(eventId, correlationId, customerId, action, String.valueOf(e.getSeq()),
                        Map.of("actor", actor, "result", result)));
        return e;
    }

    public List<AuditEventEntity> trail(String eventId) {
        return repo.findByEventIdOrderBySeqAsc(eventId);
    }
}
