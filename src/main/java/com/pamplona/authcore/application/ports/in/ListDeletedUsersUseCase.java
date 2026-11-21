package com.pamplona.authcore.application.ports.in;

import com.pamplona.authcore.domain.DeletedUser;

import java.util.List;

/**
 * Puerto de entrada: listar las cuentas eliminadas (solo ADMIN).
 */
public interface ListDeletedUsersUseCase {
    List<DeletedUser> listDeletedUsers();
}
