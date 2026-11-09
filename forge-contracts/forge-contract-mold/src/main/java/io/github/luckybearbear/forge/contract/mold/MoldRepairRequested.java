package io.github.luckybearbear.forge.contract.mold;

import java.time.Instant;

/** 模具报修事件：mold 模块发布，maintenance 模块监听。 */
public record MoldRepairRequested(String moldId, String description, Instant occurredAt) {}
