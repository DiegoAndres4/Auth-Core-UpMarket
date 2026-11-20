package com.pamplona.authcore.application.ports.in;

import com.pamplona.authcore.domain.User;

/**
 * Puerto de entrada: bloquear o desbloquear una cuenta (solo ADMIN).
 */
public interface BlockUserUseCase {
    User setBlocked(Long userId, boolean blocked, String requesterUsername);
}
