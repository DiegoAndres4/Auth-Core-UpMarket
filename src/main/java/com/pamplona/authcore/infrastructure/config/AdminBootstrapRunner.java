package com.pamplona.authcore.infrastructure.config;

import com.pamplona.authcore.application.ports.out.PasswordHasherPort;
import com.pamplona.authcore.application.ports.out.UserRepositoryPort;
import com.pamplona.authcore.domain.Role;
import com.pamplona.authcore.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;
import java.util.HashSet;
import java.util.Set;

/**
 * Crea un usuario admin por defecto al arrancar, SOLO si no existe todavia.
 * Resuelve el problema de "quien asigna el primer rol ADMIN" sin necesitar
 * acceso directo a la base de datos.
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final String DEFAULT_ADMIN_USERNAME = "admin";

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final String configuredPassword;

    public AdminBootstrapRunner(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher,
                                @Value("${authcore.admin.password:}") String configuredPassword) {
        this.configuredPassword = configuredPassword;
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername(DEFAULT_ADMIN_USERNAME).isPresent()) {
            return;
        }
        if (configuredPassword == null || configuredPassword.isBlank()) {
            throw new IllegalStateException(
                    "AUTHCORE_ADMIN_PASSWORD es obligatorio para crear el usuario admin inicial");
        }
        Set<Role> roles = new HashSet<>(Set.of(Role.ADMIN, Role.USER));
        userRepository.save(new User(null, DEFAULT_ADMIN_USERNAME, passwordHasher.hash(configuredPassword), roles));
        log.warn("Usuario admin creado con la contrasena de AUTHCORE_ADMIN_PASSWORD.");
    }
}
