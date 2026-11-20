package com.pamplona.authcore.infrastructure.adapters.out.persistence;

import com.pamplona.authcore.application.ports.out.EmailVerificationTokenRepositoryPort;
import com.pamplona.authcore.domain.EmailVerificationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class EmailVerificationTokenRepositoryJpaAdapter implements EmailVerificationTokenRepositoryPort {

    private final EmailVerificationTokenJpaRepository jpaRepository;
    private final UserJpaRepository userJpaRepository;

    public EmailVerificationTokenRepositoryJpaAdapter(EmailVerificationTokenJpaRepository jpaRepository,
                                                      UserJpaRepository userJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        UserJpaEntity user = userJpaRepository.getReferenceById(token.getUserId());
        EmailVerificationTokenJpaEntity entity = new EmailVerificationTokenJpaEntity(
                token.getId(),
                user,
                token.getTokenHash(),
                token.getExpiresAt(),
                token.getUsedAt(),
                token.getCreatedAt());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<EmailVerificationToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public Optional<EmailVerificationToken> findLatestByUserId(Long userId) {
        return jpaRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId).map(this::toDomain);
    }

    @Override
    public void invalidateAllByUserId(Long userId, Instant usedAt) {
        jpaRepository.invalidateAllByUserId(userId, usedAt);
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        jpaRepository.deleteAllByUserId(userId);
    }

    private EmailVerificationToken toDomain(EmailVerificationTokenJpaEntity entity) {
        return new EmailVerificationToken(
                entity.getId(),
                entity.getUser().getId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getUsedAt(),
                entity.getCreatedAt());
    }
}
