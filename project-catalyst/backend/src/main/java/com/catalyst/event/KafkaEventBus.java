package com.catalyst.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-backed bus. Enabled with CATALYST_BUS=kafka.
 */
@Configuration
@ConditionalOnProperty(name = "catalyst.bus", havingValue = "kafka")
public class KafkaEventBus implements EventBus {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventBus.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public KafkaEventBus(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @Override
    public void publish(String topic, String key, Object payload) {
        try {
            kafka.send(topic, key, mapper.writeValueAsString(payload)).whenComplete((r, ex) -> {
                if (ex != null) log.error("Kafka publish to {} failed: {}", topic, ex.toString());
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event for " + topic, e);
        }
    }

    @Bean
    KafkaAdmin.NewTopics catalystTopics() {
        var topics = new org.apache.kafka.clients.admin.NewTopic[Topics.ALL.length];
        for (int i = 0; i < topics.length; i++)
            topics[i] = TopicBuilder.name(Topics.ALL[i]).partitions(1).replicas(1).build();
        return new KafkaAdmin.NewTopics(topics);
    }

    @Component
    @ConditionalOnProperty(name = "catalyst.bus", havingValue = "kafka")
    static class Listeners {
        private final PipelineHandlers handlers;

        Listeners(PipelineHandlers handlers) {
            this.handlers = handlers;
        }

        @KafkaListener(topics = Topics.LIQUIDITY_EVENTS, groupId = "catalyst-decisioning")
        void onLiquidityEvent(String json) {
            handlers.handle(Topics.LIQUIDITY_EVENTS, json);
        }

        @KafkaListener(topics = Topics.FINANCIAL_ACTIONS, groupId = "catalyst-execution")
        void onFinancialAction(String json) {
            handlers.handle(Topics.FINANCIAL_ACTIONS, json);
        }
    }
}
