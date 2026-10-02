package com.catalyst.event;

import java.util.Map;

/**
 * Generic message for downstream topics (context, recommendation, action, audit). Carries ids, not PII.
 */
public record StageMessage(String eventId, String correlationId, String customerId, String stage, String refId,
                           Map<String, Object> data) {
}
