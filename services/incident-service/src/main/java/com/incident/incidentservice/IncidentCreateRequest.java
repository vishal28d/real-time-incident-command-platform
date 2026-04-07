package com.incident.incidentservice;

import jakarta.validation.constraints.NotBlank;

public record IncidentCreateRequest(
        @NotBlank String title,
        @NotBlank String serviceName,
        @NotBlank String severity
) {
}
