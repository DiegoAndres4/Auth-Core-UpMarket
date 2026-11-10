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
import java.security.SecureRandom;
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
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

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
        if (userRepository.findByUsername(DEFAULT_ADMIN_USERNAME).isEmpty()) {
            boolean generada = configuredPassword == null || configuredPassword.isBlank();
            String password = generada ? generarClave() : configuredPassword;
            Set<Role> roles = new HashSet<>(Set.of(Role.ADMIN, Role.USER));
            userRepository.save(new User(null, DEFAULT_ADMIN_USERNAME, passwordHasher.hash(password), roles));
            if (generada) {
                log.warn("Usuario admin creado con contrasena ALEATORIA (se muestra una sola vez): {}", password);
            } else {
                log.warn("Usuario admin creado con la contrasena de AUTHCORE_ADMIN_PASSWORD.");
            }
        }
    }

    private static String generarClave() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
