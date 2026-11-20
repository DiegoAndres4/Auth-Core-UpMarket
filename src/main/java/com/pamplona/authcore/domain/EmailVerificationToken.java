package com.pamplona.authcore.domain;

import java.time.Instant;

/**
 * Token de verificacion de correo. Solo se persiste el hash SHA-256 del token;
 * el valor en claro vive unicamente en el enlace enviado por correo.
 */
public class EmailVerificationToken {

    private Long id;
    private Long userId;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
    private Instant createdAt;

    public EmailVerificationToken(Long id, Long userId, String tokenHash, Instant expiresAt,
                                  Instant usedAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
    }

    public void markUsed(Instant when) {
        this.usedAt = when;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
