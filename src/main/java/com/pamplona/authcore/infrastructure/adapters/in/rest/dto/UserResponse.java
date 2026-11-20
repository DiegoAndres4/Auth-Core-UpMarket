package com.pamplona.authcore.infrastructure.adapters.in.rest.dto;

import com.pamplona.authcore.domain.Role;
import com.pamplona.authcore.domain.User;

import java.util.Set;

public record UserResponse(Long id, String username, Set<Role> roles, boolean emailVerified, boolean blocked) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRoles(),
                user.isEmailVerified(),
                user.isBlocked());
    }
}
