package com.incident.ingestionservice;

import jakarta.validation.constraints.NotBlank;

public record AlertRequest(
        @NotBlank String serviceName,
        @NotBlank String severity,
        @NotBlank String message
) {
}
