package com.pamplona.authcore.application;

import com.pamplona.authcore.application.exception.AccountBlockedException;
import com.pamplona.authcore.application.exception.EmailNotVerifiedException;
import com.pamplona.authcore.application.exception.EmailSendException;
import com.pamplona.authcore.application.exception.InvalidCredentialsException;
import com.pamplona.authcore.application.exception.TokenExpiradoException;
import com.pamplona.authcore.application.exception.TokenInvalidoException;
import com.pamplona.authcore.application.exception.UserAlreadyExistsException;
import com.pamplona.authcore.application.ports.out.EmailSenderPort;
import com.pamplona.authcore.application.ports.out.EmailVerificationTokenRepositoryPort;
import com.pamplona.authcore.application.ports.out.PasswordHasherPort;
import com.pamplona.authcore.application.ports.out.TokenProviderPort;
import com.pamplona.authcore.application.ports.out.UserRepositoryPort;
import com.pamplona.authcore.application.service.AuthService;
import com.pamplona.authcore.domain.EmailVerificationToken;
import com.pamplona.authcore.domain.Role;
import com.pamplona.authcore.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias puras (sin Spring ni base de datos). */
class AuthServiceTest {

    private static final String PUBLIC_URL = "http://localhost";

    private UserRepositoryPort repo;
    private PasswordHasherPort hasher;
    private TokenProviderPort tokens;
    private EmailVerificationTokenRepositoryPort tokenRepo;
    private RecordingEmailSender emailSender;
    private MutableClock clock;
    private AuthService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepositoryPort.class);
        hasher = mock(PasswordHasherPort.class);
        tokens = mock(TokenProviderPort.class);
        tokenRepo = mock(EmailVerificationTokenRepositoryPort.class);
        emailSender = new RecordingEmailSender();
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = new AuthService(repo, hasher, tokens, tokenRepo, emailSender, clock, PUBLIC_URL);
        when(tokenRepo.save(any(EmailVerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registrarCreaUsuarioNoVerificadoPersisteHashYEnviaCorreo() {
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.empty());
        when(hasher.hash("secreto1")).thenReturn("HASH");
        when(repo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });

        User saved = service.register("ana@x.co", "secreto1");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(userCaptor.capture());
        assertEquals(7L, saved.getId());
        assertEquals("HASH", userCaptor.getValue().getPasswordHash());
        assertEquals(Set.of(Role.USER), userCaptor.getValue().getRoles());
        assertFalse(saved.isEmailVerified());
        assertFalse(saved.isBlocked());

        assertEquals(1, emailSender.sent.size());
        RecordingEmailSender.SentEmail email = emailSender.sent.get(0);
        assertEquals("ana@x.co", email.recipient());
        assertTrue(email.link().startsWith(PUBLIC_URL + "/#/verificar?token="));

        String rawToken = email.link().substring(email.link().indexOf("token=") + "token=".length());
        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepo).save(tokenCaptor.capture());
        EmailVerificationToken token = tokenCaptor.getValue();
        assertEquals(sha256Hex(rawToken), token.getTokenHash());
        assertEquals(64, token.getTokenHash().length());
        assertFalse(token.isUsed());
        assertEquals(clock.instant(), token.getCreatedAt());
        assertEquals(clock.instant().plus(Duration.ofHours(24)), token.getExpiresAt());
    }

    @Test
    void registrarUsuarioExistenteLanzaConflictoYNoEnviaCorreo() {
        when(repo.findByUsername("ana@x.co"))
                .thenReturn(Optional.of(new User(1L, "ana@x.co", "H", Set.of(Role.USER))));

        assertThrows(UserAlreadyExistsException.class, () -> service.register("ana@x.co", "secreto1"));
        verify(repo, never()).save(any());
        assertTrue(emailSender.sent.isEmpty());
    }

    @Test
    void registrarConFalloDeCorreoDejaUsuarioCreadoYTokenPersistido() {
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.empty());
        when(hasher.hash("secreto1")).thenReturn("HASH");
        when(repo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(9L);
            return u;
        });
        emailSender.failNext = true;

        User saved = service.register("ana@x.co", "secreto1");

        assertEquals(9L, saved.getId());
        assertFalse(saved.isEmailVerified());
        verify(tokenRepo).save(any(EmailVerificationToken.class));
        assertTrue(emailSender.sent.isEmpty());
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
    void loginBloqueadoLanzaCuentaBloqueada() {
        User u = new User(1L, "ana@x.co", "H", Set.of(Role.USER), true, true);
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.of(u));
        when(hasher.matches("secreto1", "H")).thenReturn(true);

        assertThrows(AccountBlockedException.class, () -> service.login("ana@x.co", "secreto1"));
    }

    @Test
    void loginNoVerificadoLanzaEmailNoVerificado() {
        User u = new User(1L, "ana@x.co", "H", Set.of(Role.USER), false, false);
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.of(u));
        when(hasher.matches("secreto1", "H")).thenReturn(true);

        assertThrows(EmailNotVerifiedException.class, () -> service.login("ana@x.co", "secreto1"));
    }

    @Test
    void laClaveSeValidaAntesQueElEstadoDeLaCuenta() {
        User u = new User(1L, "ana@x.co", "H", Set.of(Role.USER), false, true);
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.of(u));
        when(hasher.matches("mala", "H")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> service.login("ana@x.co", "mala"));
    }

    @Test
    void verificarCorreoMarcaUsuarioYTokenComoUsados() {
        String rawToken = "token-valido";
        EmailVerificationToken token = new EmailVerificationToken(
                5L, 1L, sha256Hex(rawToken), clock.instant().plusSeconds(60), null, clock.instant());
        User user = new User(1L, "ana@x.co", "H", Set.of(Role.USER), false, false);
        when(tokenRepo.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(token));
        when(repo.findById(1L)).thenReturn(Optional.of(user));

        service.verifyEmail(rawToken);

        assertTrue(user.isEmailVerified());
        assertTrue(token.isUsed());
        verify(repo).save(user);
        verify(tokenRepo).save(token);
    }

    @Test
    void verificarTokenYaUsadoLanzaInvalido() {
        String rawToken = "token-usado";
        EmailVerificationToken token = new EmailVerificationToken(
                5L, 1L, sha256Hex(rawToken), clock.instant().plusSeconds(60), clock.instant(), clock.instant());
        when(tokenRepo.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(token));

        assertThrows(TokenInvalidoException.class, () -> service.verifyEmail(rawToken));
        verify(repo, never()).save(any());
    }

    @Test
    void verificarTokenExpiradoLanzaExpirado() {
        String rawToken = "token-expirado";
        EmailVerificationToken token = new EmailVerificationToken(
                5L, 1L, sha256Hex(rawToken), clock.instant().minusSeconds(1), null, clock.instant().minusSeconds(60));
        when(tokenRepo.findByTokenHash(sha256Hex(rawToken))).thenReturn(Optional.of(token));

        assertThrows(TokenExpiradoException.class, () -> service.verifyEmail(rawToken));
        verify(repo, never()).save(any());
    }

    @Test
    void verificarTokenInexistenteLanzaInvalido() {
        when(tokenRepo.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThrows(TokenInvalidoException.class, () -> service.verifyEmail("basura"));
    }

    @Test
    void reenvioUsuarioDesconocidoNoHaceNada() {
        when(repo.findByUsername("nadie@x.co")).thenReturn(Optional.empty());

        service.resendVerification("nadie@x.co");

        assertTrue(emailSender.sent.isEmpty());
        verify(tokenRepo, never()).save(any());
    }

    @Test
    void reenvioUsuarioYaVerificadoNoHaceNada() {
        User user = new User(1L, "ana@x.co", "H", Set.of(Role.USER), true, false);
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.of(user));

        service.resendVerification("ana@x.co");

        assertTrue(emailSender.sent.isEmpty());
        verify(tokenRepo, never()).save(any());
    }

    @Test
    void reenvioDentroDeSesentaSegundosNoEnviaOtroCorreo() {
        User user = new User(1L, "ana@x.co", "H", Set.of(Role.USER), false, false);
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.of(user));
        when(tokenRepo.findLatestByUserId(1L)).thenReturn(Optional.of(new EmailVerificationToken(
                3L, 1L, "HASH", clock.instant().plusSeconds(60), null, clock.instant().minusSeconds(30))));

        service.resendVerification("ana@x.co");

        assertTrue(emailSender.sent.isEmpty());
        verify(tokenRepo, never()).invalidateAllByUserId(any(), any());
        verify(tokenRepo, never()).save(any());
    }

    @Test
    void reenvioTrasSesentaSegundosInvalidaYEnviaNuevoCorreo() {
        User user = new User(1L, "ana@x.co", "H", Set.of(Role.USER), false, false);
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.of(user));
        when(tokenRepo.findLatestByUserId(1L)).thenReturn(Optional.of(new EmailVerificationToken(
                3L, 1L, "HASH", clock.instant().minusSeconds(1), null, clock.instant().minusSeconds(61))));

        service.resendVerification("ana@x.co");

        verify(tokenRepo).invalidateAllByUserId(1L, clock.instant());
        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepo).save(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals(1, emailSender.sent.size());
        assertTrue(emailSender.sent.get(0).link().contains("/#/verificar?token="));
    }

    @Test
    void reenvioSinTokenPrevioEnviaCorreo() {
        User user = new User(1L, "ana@x.co", "H", Set.of(Role.USER), false, false);
        when(repo.findByUsername("ana@x.co")).thenReturn(Optional.of(user));
        when(tokenRepo.findLatestByUserId(1L)).thenReturn(Optional.empty());

        service.resendVerification("ana@x.co");

        verify(tokenRepo).save(any(EmailVerificationToken.class));
        assertEquals(1, emailSender.sent.size());
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

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class RecordingEmailSender implements EmailSenderPort {
        private final List<SentEmail> sent = new ArrayList<>();
        private boolean failNext;

        @Override
        public void sendVerificationEmail(String recipient, String verificationLink) {
            if (failNext) {
                failNext = false;
                throw new EmailSendException("fallo simulado", new RuntimeException("smtp caido"));
            }
            sent.add(new SentEmail(recipient, verificationLink));
        }

        private record SentEmail(String recipient, String link) {
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
