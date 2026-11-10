package com.pamplona.authcore.infrastructure.adapters.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "username es obligatorio")
        @Size(max = 100, message = "username admite maximo 100 caracteres") String username,
        @NotBlank(message = "password es obligatorio")
        @Size(min = 6, max = 72, message = "password debe tener entre 6 y 72 caracteres") String password
) {}
