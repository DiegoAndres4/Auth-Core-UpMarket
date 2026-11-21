package com.pamplona.authcore.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeletedUserJpaRepository extends JpaRepository<DeletedUserJpaEntity, Long> {

    List<DeletedUserJpaEntity> findAllByOrderByDeletedAtDescIdDesc();
}
