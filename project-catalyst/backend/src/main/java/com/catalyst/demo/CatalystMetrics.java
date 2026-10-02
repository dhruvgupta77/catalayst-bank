package com.catalyst.demo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CatalystMetrics {
    public static final String[] COUNTERS = {"liquidity.events.received", "liquidity.events.processed",
            "recommendations.generated", "recommendations.accepted", "actions.executed", "actions.failed"};

    private final MeterRegistry registry;
    private final Timer processing;
    private final Timer recommendation;

    public CatalystMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (String c : COUNTERS) Counter.builder(c).register(registry);
        this.processing = Timer.builder("liquidity.events.processing.duration").register(registry);
        this.recommendation = Timer.builder("recommendations.generation.duration").register(registry);
    }

    public void inc(String name) {
        registry.counter(name).increment();
    }

    public double count(String name) {
        return registry.counter(name).count();
    }

    public void processing(Duration d) {
        processing.record(d);
    }

    public void recommendation(Duration d) {
        recommendation.record(d);
    }
}
