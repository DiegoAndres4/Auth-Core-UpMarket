package com.pamplona.authcore.infrastructure.adapters.in.rest.dto;

import jakarta.validation.constraints.NotNull;

public record BlockUserRequest(
        @NotNull(message = "blocked es obligatorio") Boolean blocked
) {}
