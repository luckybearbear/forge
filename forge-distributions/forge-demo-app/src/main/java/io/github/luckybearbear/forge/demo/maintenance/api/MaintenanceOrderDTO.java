package io.github.luckybearbear.forge.demo.maintenance.api;

import java.time.Instant;

public record MaintenanceOrderDTO(String id, String moldId, String moldName,
                                  String description, String status, Instant createdAt) {}
