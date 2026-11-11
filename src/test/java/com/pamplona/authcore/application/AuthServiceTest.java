package com.pamplona.authcore.application;

import com.pamplona.authcore.application.exception.InvalidCredentialsException;
import com.pamplona.authcore.application.exception.UserAlreadyExistsException;
import com.pamplona.authcore.application.ports.out.PasswordHasherPort;
import com.pamplona.authcore.application.ports.out.TokenProviderPort;
import com.pamplona.authcore.application.ports.out.UserRepositoryPort;
import com.pamplona.authcore.application.service.AuthService;
import com.pamplona.authcore.domain.Role;
import com.pamplona.authcore.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias puras (sin Spring ni base de datos). */
class AuthServiceTest {

    private UserRepositoryPort repo;
    private PasswordHasherPort hasher;
    private TokenProviderPort tokens;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepositoryPort.class);
        hasher = mock(PasswordHasherPort.class);
        tokens = mock(TokenProviderPort.class);
        service = new AuthService(repo, hasher, tokens);
    }

    @Test
    void registrarCreaUsuarioConRolUserYClaveHasheada() {
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.empty());
        when(hasher.hash("secreto1")).thenReturn("HASH");
        when(repo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });

        Long id = service.register("ana@x.co", "secreto1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(captor.capture());
        assertEquals(7L, id);
        assertEquals("HASH", captor.getValue().getPasswordHash());
        assertEquals(Set.of(Role.USER), captor.getValue().getRoles());
    }

    @Test
    void registrarUsuarioExistenteLanzaConflicto() {
        when(repo.findByUsername("ana@x.co"))
                .thenReturn(Optional.of(new User(1L, "ana@x.co", "H", Set.of(Role.USER))));

        assertThrows(UserAlreadyExistsException.class, () -> service.register("ana@x.co", "secreto1"));
        verify(repo, never()).save(any());
    }

    @Test
    void loginCorrectoDevuelveToken() {
        User u = new User(1L, "ana@x.co", "H", Set.of(Role.USER));
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.of(u));
        when(hasher.matches("secreto1", "H")).thenReturn(true);
        when(tokens.generateToken(u)).thenReturn("TOKEN");

        assertEquals("TOKEN", service.login("ana@x.co", "secreto1"));
    }

    @Test
    void loginConClaveErradaOUsuarioInexistenteFalla() {
        User u = new User(1L, "ana@x.co", "H", Set.of(Role.USER));
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.of(u));
        when(hasher.matches("mala", "H")).thenReturn(false);
        when(repo.findByUsername("nadie")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> service.login("ana@x.co", "mala"));
        assertThrows(InvalidCredentialsException.class, () -> service.login("nadie", "x"));
    }

    @Test
    void asignarRolAgregaElRolSinPerderLosAnteriores() {
        User u = new User(1L, "ana@x.co", "H", Set.of(Role.USER));
        when(repo.findById(1L)).thenReturn(Optional.of(u));

        service.assignRole(1L, Role.ADMIN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(captor.capture());
        assertTrue(captor.getValue().hasRole(Role.ADMIN));
        assertTrue(captor.getValue().hasRole(Role.USER));
    }

    @Test
    void eliminarCuentaBorraLasCredencialesPorUsername() {
        service.deleteByUsername("ana@x.co");

        verify(repo).deleteByUsername("ana@x.co");
    }
}
