package io.github.luckybearbear.forge.platform.messaging;

/** 消息发布抽象 SPI。Phase 3 按需提供实现；模块内默认事件通道是 Spring Modulith 事件。 */
public interface MessagePublisher {

    void publish(String topic, Object payload);
}
