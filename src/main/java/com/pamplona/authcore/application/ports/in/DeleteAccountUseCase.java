package com.pamplona.authcore.application.ports.in;

/**
 * Puerto de entrada: eliminar las credenciales de una cuenta.
 * Lo invoca dominio-service cuando el usuario borra su cuenta, para que el
 * username quede libre y el login deje de funcionar.
 */
public interface DeleteAccountUseCase {
    void deleteByUsername(String username);
}
