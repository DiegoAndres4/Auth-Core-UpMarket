package com.pamplona.authcore.application.ports.out;

import com.pamplona.authcore.domain.DeletedUser;

import java.util.List;

/**
 * Puerto de salida: persistencia de las cuentas eliminadas (archivo historico).
 * Los registros archivados son de solo lectura: no se actualizan ni se borran.
 */
public interface DeletedUserRepositoryPort {

    DeletedUser save(DeletedUser deletedUser);

    /** Devuelve las cuentas archivadas de la mas reciente a la mas antigua. */
    List<DeletedUser> findAllNewestFirst();
}
