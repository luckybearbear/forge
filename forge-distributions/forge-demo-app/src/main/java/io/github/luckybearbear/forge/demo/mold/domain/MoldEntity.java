package io.github.luckybearbear.forge.demo.mold.domain;

import io.github.luckybearbear.forge.contract.mold.MoldStatus;
import java.time.Instant;

/** 内存域对象（Phase 1 无 DB；Phase 2 换 Spring Data JDBC 实体）。 */
public record MoldEntity(String id, String name, String specification, MoldStatus status, Instant createdAt) {}
