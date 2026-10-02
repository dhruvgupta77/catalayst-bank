package com.catalyst.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single-JVM bus with the same contract as Kafka; lets the demo run with zero infrastructure.
 */
@Component
@ConditionalOnProperty(name = "catalyst.bus", havingValue = "memory", matchIfMissing = true)
public class InMemoryEventBus implements EventBus {
    private static final Logger log = LoggerFactory.getLogger(InMemoryEventBus.class);

    private final ObjectMapper mapper;
    private final ObjectProvider<PipelineHandlers> handlers;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "mem-bus");
        t.setDaemon(true);
        return t;
    });

    public InMemoryEventBus(ObjectMapper mapper, ObjectProvider<PipelineHandlers> handlers) {
        this.mapper = mapper;
        this.handlers = handlers;
    }

    @Override
    public void publish(String topic, String key, Object payload) {
        final String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event for " + topic, e);
        }
        log.debug("bus[memory] -> {} key={}", topic, key);
        executor.submit(() -> {
            try {
                handlers.getObject().handle(topic, json);
            } catch (RuntimeException e) {
                log.error("Handler failed for topic {}: {}", topic, e.toString());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
