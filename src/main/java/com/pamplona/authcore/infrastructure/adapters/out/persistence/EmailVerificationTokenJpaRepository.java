package com.pamplona.authcore.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationTokenJpaRepository extends JpaRepository<EmailVerificationTokenJpaEntity, Long> {

    Optional<EmailVerificationTokenJpaEntity> findByTokenHash(String tokenHash);

    Optional<EmailVerificationTokenJpaEntity> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Modifying
    @Query("update EmailVerificationTokenJpaEntity t set t.usedAt = :usedAt "
            + "where t.user.id = :userId and t.usedAt is null")
    int invalidateAllByUserId(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);

    void deleteAllByUserId(Long userId);
}
