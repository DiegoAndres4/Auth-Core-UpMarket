package com.pamplona.authcore.infrastructure.adapters.in.rest.dto;

import com.pamplona.authcore.domain.DeletedUser;
import com.pamplona.authcore.domain.Role;

import java.time.Instant;
import java.util.Set;

/**
 * Representacion de una cuenta eliminada. Nunca incluye contrasena ni hash.
 */
public record DeletedUserResponse(
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

    public static DeletedUserResponse from(DeletedUser deletedUser) {
        return new DeletedUserResponse(
                deletedUser.id(),
                deletedUser.originalUserId(),
                deletedUser.username(),
                deletedUser.roles(),
                deletedUser.emailVerified(),
                deletedUser.blocked(),
                deletedUser.createdAt(),
                deletedUser.deletedAt(),
                deletedUser.deletedBy(),
                deletedUser.reason());
    }
}
