package com.pamplona.authcore.infrastructure.adapters.out.persistence;

import com.pamplona.authcore.application.ports.out.DeletedUserRepositoryPort;
import com.pamplona.authcore.domain.DeletedUser;
import com.pamplona.authcore.domain.Role;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adaptador de salida: traduce entre DeletedUser (dominio) y DeletedUserJpaEntity.
 * Los roles se guardan como texto separado por comas para que el archivo sea
 * legible y consultable sin una tabla adicional.
 */
@Component
public class DeletedUserRepositoryJpaAdapter implements DeletedUserRepositoryPort {

    private final DeletedUserJpaRepository jpaRepository;

    public DeletedUserRepositoryJpaAdapter(DeletedUserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public DeletedUser save(DeletedUser deletedUser) {
        DeletedUserJpaEntity entity = new DeletedUserJpaEntity(
                deletedUser.id(),
                deletedUser.originalUserId(),
                deletedUser.username(),
                serializeRoles(deletedUser.roles()),
                deletedUser.emailVerified(),
                deletedUser.blocked(),
                deletedUser.createdAt(),
                deletedUser.deletedAt(),
                deletedUser.deletedBy(),
                deletedUser.reason());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public List<DeletedUser> findAllNewestFirst() {
        return jpaRepository.findAllByOrderByDeletedAtDescIdDesc().stream()
                .map(this::toDomain)
                .toList();
    }

    private DeletedUser toDomain(DeletedUserJpaEntity entity) {
        return new DeletedUser(
                entity.getId(),
                entity.getOriginalUserId(),
                entity.getUsername(),
                parseRoles(entity.getRoles()),
                entity.isEmailVerified(),
                entity.isBlocked(),
                entity.getCreatedAt(),
                entity.getDeletedAt(),
                entity.getDeletedBy(),
                entity.getReason());
    }

    private static String serializeRoles(Set<Role> roles) {
        return roles.stream().map(Role::name).sorted().collect(Collectors.joining(","));
    }

    private static Set<Role> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }
}
