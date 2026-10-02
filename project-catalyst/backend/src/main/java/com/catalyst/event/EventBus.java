package com.catalyst.event;

public interface EventBus {
    void publish(String topic, String key, Object payload);
}
