package io.github.luckybearbear.forge.demo.maintenance.domain;

import java.time.Instant;

public record MaintenanceOrder(String id, String moldId, String moldName,
                               String description, String status, Instant createdAt) {}
