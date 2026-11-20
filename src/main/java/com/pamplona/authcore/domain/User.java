package com.pamplona.authcore.domain;

import java.util.HashSet;
import java.util.Set;

/**
 * Entidad de dominio pura: no tiene NINGUNA anotacion de Spring, JPA ni de
 * ningun framework. Esto es intencional - es el nucleo hexagonal y debe
 * poder compilarse y probarse sin depender de infraestructura.
 */
public class User {

    private Long id;
    private String username;
    private String passwordHash;
    private Set<Role> roles;
    private boolean emailVerified;
    private boolean blocked;

    /**
     * Constructor de compatibilidad para cuentas ya establecidas: se asume
     * correo verificado y cuenta no bloqueada (es el estado de toda cuenta
     * previa a la verificacion de correo).
     */
    public User(Long id, String username, String passwordHash, Set<Role> roles) {
        this(id, username, passwordHash, roles, true, false);
    }

    public User(Long id, String username, String passwordHash, Set<Role> roles,
                boolean emailVerified, boolean blocked) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        this.emailVerified = emailVerified;
        this.blocked = blocked;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Set<Role> getRoles() {
        return new HashSet<>(roles);
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
}
