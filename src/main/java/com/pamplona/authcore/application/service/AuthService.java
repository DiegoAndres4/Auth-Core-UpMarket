package com.pamplona.authcore.application.service;

import com.pamplona.authcore.application.exception.AccountBlockedException;
import com.pamplona.authcore.application.exception.CannotBlockAdminException;
import com.pamplona.authcore.application.exception.CannotBlockSelfException;
import com.pamplona.authcore.application.exception.EmailNotVerifiedException;
import com.pamplona.authcore.application.exception.InvalidCredentialsException;
import com.pamplona.authcore.application.exception.TokenExpiradoException;
import com.pamplona.authcore.application.exception.TokenInvalidoException;
import com.pamplona.authcore.application.exception.UserAlreadyExistsException;
import com.pamplona.authcore.application.exception.UserNotFoundException;
import com.pamplona.authcore.application.ports.in.AssignRoleUseCase;
import com.pamplona.authcore.application.ports.in.BlockUserUseCase;
import com.pamplona.authcore.application.ports.in.DeleteAccountUseCase;
import com.pamplona.authcore.application.ports.in.ListUsersUseCase;
import com.pamplona.authcore.application.ports.in.LoginUseCase;
import com.pamplona.authcore.application.ports.in.RegisterUserUseCase;
import com.pamplona.authcore.application.ports.in.ResendVerificationUseCase;
import com.pamplona.authcore.application.ports.in.VerifyEmailUseCase;
import com.pamplona.authcore.application.ports.out.EmailSenderPort;
import com.pamplona.authcore.application.ports.out.EmailVerificationTokenRepositoryPort;
import com.pamplona.authcore.application.ports.out.PasswordHasherPort;
import com.pamplona.authcore.application.ports.out.TokenProviderPort;
import com.pamplona.authcore.application.ports.out.UserRepositoryPort;
import com.pamplona.authcore.domain.EmailVerificationToken;
import com.pamplona.authcore.domain.Role;
import com.pamplona.authcore.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Servicio de aplicacion: implementa los casos de uso (puertos de entrada)
 * usando SOLO los puertos de salida. No importa nada de "infrastructure".
 */
@Service
public class AuthService implements RegisterUserUseCase, LoginUseCase, AssignRoleUseCase, ListUsersUseCase,
        DeleteAccountUseCase, VerifyEmailUseCase, ResendVerificationUseCase, BlockUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final Duration RESEND_WINDOW = Duration.ofSeconds(60);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepositoryPort userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenProviderPort tokenProvider;
    private final EmailVerificationTokenRepositoryPort verificationTokenRepository;
    private final EmailSenderPort emailSender;
    private final Clock clock;
    private final String publicUrl;

    public AuthService(UserRepositoryPort userRepository,
                       PasswordHasherPort passwordHasher,
                       TokenProviderPort tokenProvider,
                       EmailVerificationTokenRepositoryPort verificationTokenRepository,
                       EmailSenderPort emailSender,
                       Clock clock,
                       @Value("${authcore.public-url:http://localhost}") String publicUrl) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenProvider = tokenProvider;
        this.verificationTokenRepository = verificationTokenRepository;
        this.emailSender = emailSender;
        this.clock = clock;
        this.publicUrl = publicUrl != null && publicUrl.endsWith("/")
                ? publicUrl.substring(0, publicUrl.length() - 1)
                : publicUrl;
    }

    @Override
    @Transactional
    public User register(String username, String rawPassword) {
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new UserAlreadyExistsException(username);
        });
        Set<Role> defaultRoles = new HashSet<>(Set.of(Role.USER));
        User user = new User(null, username, passwordHasher.hash(rawPassword), defaultRoles, false, false);
        User saved = userRepository.save(user);
        issueVerificationToken(saved);
        return saved;
    }

    @Override
    public String login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordHasher.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (user.isBlocked()) {
            throw new AccountBlockedException();
        }
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        return tokenProvider.generateToken(user);
    }

    @Override
    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = verificationTokenRepository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(TokenInvalidoException::new);
        if (token.isUsed()) {
            throw new TokenInvalidoException();
        }
        Instant now = clock.instant();
        if (token.isExpired(now)) {
            throw new TokenExpiradoException();
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(TokenInvalidoException::new);
        user.markEmailVerified();
        userRepository.save(user);
        token.markUsed(now);
        verificationTokenRepository.save(token);
    }

    @Override
    @Transactional
    public void resendVerification(String username) {
        Optional<User> maybeUser = userRepository.findByUsername(username);
        if (maybeUser.isEmpty() || maybeUser.get().isEmailVerified()) {
            return;
        }
        User user = maybeUser.get();
        Instant now = clock.instant();
        Optional<EmailVerificationToken> latest = verificationTokenRepository.findLatestByUserId(user.getId());
        if (latest.isPresent() && latest.get().getCreatedAt().isAfter(now.minus(RESEND_WINDOW))) {
            return;
        }
        verificationTokenRepository.invalidateAllByUserId(user.getId(), now);
        issueVerificationToken(user);
    }

    @Override
    @Transactional
    public User setBlocked(Long userId, boolean blocked, String requesterUsername) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        User requester = userRepository.findByUsername(requesterUsername).orElse(null);
        if (blocked && requester != null && requester.getId().equals(target.getId())) {
            throw new CannotBlockSelfException();
        }
        if (blocked && target.hasRole(Role.ADMIN)) {
            throw new CannotBlockAdminException();
        }
        target.setBlocked(blocked);
        return userRepository.save(target);
    }

    @Override
    public void assignRole(Long userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        user.addRole(role);
        userRepository.save(user);
    }

    @Override
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @Override
    public void deleteByUsername(String username) {
        userRepository.deleteByUsername(username);
    }

    private void issueVerificationToken(User user) {
        String rawToken = generateRawToken();
        Instant now = clock.instant();
        EmailVerificationToken token = new EmailVerificationToken(
                null,
                user.getId(),
                sha256Hex(rawToken),
                now.plus(TOKEN_TTL),
                null,
                now);
        verificationTokenRepository.save(token);

        String link = publicUrl + "/#/verificar?token=" + rawToken;
        try {
            emailSender.sendVerificationEmail(user.getUsername(), link);
        } catch (RuntimeException ex) {
            // El usuario ya quedo creado sin verificar: puede usar el reenvio.
            // Nunca se registra el token ni el enlace en el log de error.
            log.error("No se pudo enviar el correo de verificacion para {}", user.getUsername(), ex);
        }
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no esta disponible", ex);
        }
    }
}
