package com.catalyst.event;

import com.catalyst.application.ActionExecutionService;
import com.catalyst.application.EventProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Topic consumers shared by the Kafka and in-memory buses.
 */
@Component
public class PipelineHandlers {
    private static final Logger log = LoggerFactory.getLogger(PipelineHandlers.class);

    private final ObjectMapper mapper;
    private final EventProcessingService processing;
    private final ActionExecutionService execution;

    public PipelineHandlers(ObjectMapper mapper, EventProcessingService processing, ActionExecutionService execution) {
        this.mapper = mapper;
        this.processing = processing;
        this.execution = execution;
    }

    public void handle(String topic, String json) {
        try {
            switch (topic) {
                case Topics.LIQUIDITY_EVENTS -> processing.process(mapper.readValue(json, LiquidityEventMessage.class));
                case Topics.FINANCIAL_ACTIONS -> {
                    StageMessage m = mapper.readValue(json, StageMessage.class);
                    execution.executeApproved(m.refId(), m.correlationId());
                }
                default -> log.debug("Topic {} has no consumer in this service (projection only)", topic);
            }
        } catch (JsonProcessingException e) {
            log.error("Discarding malformed message on {}: {}", topic, e.getOriginalMessage());
        }
    }
}
