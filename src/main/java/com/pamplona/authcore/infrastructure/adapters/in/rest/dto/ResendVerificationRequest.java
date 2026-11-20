package com.pamplona.authcore.infrastructure.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationRequest(
        @NotBlank(message = "username es obligatorio")
        @Size(max = 100, message = "username admite maximo 100 caracteres") String username
) {}
