package com.pamplona.authcore.domain;

import java.time.Instant;
import java.util.Set;

/**
 * Instantanea de una cuenta eliminada. Conserva el registro para auditoria
 * sin guardar la contrasena ni ningun otro secreto de la cuenta original.
 */
public record DeletedUser(
        Long id,
        Long originalUserId,
        String username,
        Set<Role> roles,
        boolean emailVerified,
        boolean blocked,
        Instant createdAt,
        Instant deletedAt,
        String deletedBy,
        String reason) {

    public DeletedUser {
        roles = roles != null ? Set.copyOf(roles) : Set.of();
    }
}
