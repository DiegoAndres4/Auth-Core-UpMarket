package com.pamplona.authcore.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entidad JPA del archivo de cuentas eliminadas. A proposito NO guarda el hash
 * de la contrasena: una cuenta cerrada no conserva credenciales.
 */
@Entity
@Table(name = "deleted_users", indexes = {
        @Index(name = "idx_deleted_users_username", columnList = "username"),
        @Index(name = "idx_deleted_users_deleted_at", columnList = "deleted_at")
})
public class DeletedUserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_user_id", nullable = false)
    private Long originalUserId;

    @Column(nullable = false)
    private String username;

    /** Roles separados por coma, por ejemplo "ADMIN,USER". */
    @Column(nullable = false)
    private String roles;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(nullable = false)
    private boolean blocked;

    /** Fecha de creacion de la cuenta original, si la base la conoce (aqui no existe: NULL). */
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    @Column(name = "deleted_by", nullable = false)
    private String deletedBy;

    @Column(nullable = false)
    private String reason;

    protected DeletedUserJpaEntity() {
        // requerido por JPA
    }

    public DeletedUserJpaEntity(Long id, Long originalUserId, String username, String roles,
                                boolean emailVerified, boolean blocked, Instant createdAt,
                                Instant deletedAt, String deletedBy, String reason) {
        this.id = id;
        this.originalUserId = originalUserId;
        this.username = username;
        this.roles = roles;
        this.emailVerified = emailVerified;
        this.blocked = blocked;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
        this.deletedBy = deletedBy;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOriginalUserId() {
        return originalUserId;
    }

    public void setOriginalUserId(Long originalUserId) {
        this.originalUserId = originalUserId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
