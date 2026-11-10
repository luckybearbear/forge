package io.github.luckybearbear.forge.platform.security.token;

import java.time.Instant;

public record StoredToken(String token, TokenGrant grant, Instant expiresAt) {}
