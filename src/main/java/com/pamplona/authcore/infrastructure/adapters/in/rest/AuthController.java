package com.pamplona.authcore.infrastructure.adapters.in.rest;

import com.pamplona.authcore.application.ports.in.AssignRoleUseCase;
import com.pamplona.authcore.application.ports.in.BlockUserUseCase;
import com.pamplona.authcore.application.ports.in.DeleteAccountUseCase;
import com.pamplona.authcore.application.ports.in.ListUsersUseCase;
import com.pamplona.authcore.application.ports.in.LoginUseCase;
import com.pamplona.authcore.application.ports.in.RegisterUserUseCase;
import com.pamplona.authcore.application.ports.in.ResendVerificationUseCase;
import com.pamplona.authcore.application.ports.in.VerifyEmailUseCase;
import com.pamplona.authcore.domain.User;
import com.pamplona.authcore.infrastructure.adapters.in.rest.dto.AssignRoleRequest;
import com.pamplona.authcore.infrastructure.adapters.in.rest.dto.BlockUserRequest;
import com.pamplona.authcore.infrastructure.adapters.in.rest.dto.LoginRequest;
import com.pamplona.authcore.infrastructure.adapters.in.rest.dto.LoginResponse;
import com.pamplona.authcore.infrastructure.adapters.in.rest.dto.RegisterRequest;
import com.pamplona.authcore.infrastructure.adapters.in.rest.dto.RegisterResponse;
import com.pamplona.authcore.infrastructure.adapters.in.rest.dto.ResendVerificationRequest;
import com.pamplona.authcore.infrastructure.adapters.in.rest.dto.UserResponse;
import com.pamplona.authcore.infrastructure.adapters.in.rest.dto.VerifyEmailRequest;
import com.pamplona.authcore.infrastructure.adapters.in.rest.dto.VerifyEmailResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Adaptador de entrada REST. Este es el UNICO punto de contacto que
 * dominio-service (y cualquier cliente) tiene con authcore-service.
 * Contrato: ver README.md
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final AssignRoleUseCase assignRoleUseCase;
    private final ListUsersUseCase listUsersUseCase;
    private final DeleteAccountUseCase deleteAccountUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final ResendVerificationUseCase resendVerificationUseCase;
    private final BlockUserUseCase blockUserUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase,
                           LoginUseCase loginUseCase,
                           AssignRoleUseCase assignRoleUseCase,
                           ListUsersUseCase listUsersUseCase,
                           DeleteAccountUseCase deleteAccountUseCase,
                           VerifyEmailUseCase verifyEmailUseCase,
                           ResendVerificationUseCase resendVerificationUseCase,
                           BlockUserUseCase blockUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.assignRoleUseCase = assignRoleUseCase;
        this.listUsersUseCase = listUsersUseCase;
        this.deleteAccountUseCase = deleteAccountUseCase;
        this.verifyEmailUseCase = verifyEmailUseCase;
        this.resendVerificationUseCase = resendVerificationUseCase;
        this.blockUserUseCase = blockUserUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = registerUserUseCase.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegisterResponse(user.getId(), user.getUsername(), user.isEmailVerified()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = loginUseCase.login(request.username(), request.password());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        verifyEmailUseCase.verifyEmail(request.token());
        return ResponseEntity.ok(new VerifyEmailResponse(true));
    }

    /**
     * Responde siempre 202 con el mismo cuerpo para no revelar si la cuenta
     * existe. El reenvio real solo ocurre si la cuenta existe, no esta
     * verificada y el ultimo token tiene al menos 60 segundos.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        resendVerificationUseCase.resendVerification(request.username());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "mensaje", "Si la cuenta existe y no esta verificada, enviaremos un nuevo enlace de verificacion."));
    }

    @PostMapping("/users/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignRole(@PathVariable Long id, @Valid @RequestBody AssignRoleRequest request) {
        assignRoleUseCase.assignRole(id, request.role());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> listUsers() {
        List<UserResponse> users = listUsersUseCase.listUsers().stream().map(UserResponse::from).toList();
        return ResponseEntity.ok(users);
    }

    @PatchMapping("/users/{id}/blocked")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> setBlocked(@PathVariable Long id,
                                                   @Valid @RequestBody BlockUserRequest request,
                                                   Authentication authentication) {
        User user = blockUserUseCase.setBlocked(id, request.blocked(), authentication.getName());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * Borra las credenciales del usuario autenticado. Lo llama dominio-service
     * cuando el usuario elimina su cuenta; es idempotente (204 aunque ya no exista).
     */
    @DeleteMapping("/users/me")
    public ResponseEntity<Void> deleteMe(Authentication authentication) {
        deleteAccountUseCase.deleteByUsername(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
