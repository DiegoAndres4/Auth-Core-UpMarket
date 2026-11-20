package com.pamplona.authcore.application.ports.out;

import com.pamplona.authcore.domain.EmailVerificationToken;

import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de salida: persistencia de tokens de verificacion de correo.
 */
public interface EmailVerificationTokenRepositoryPort {

    EmailVerificationToken save(EmailVerificationToken token);

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    Optional<EmailVerificationToken> findLatestByUserId(Long userId);

    /** Marca como usados todos los tokens aun vigentes del usuario. */
    void invalidateAllByUserId(Long userId, Instant usedAt);

    void deleteAllByUserId(Long userId);
}
