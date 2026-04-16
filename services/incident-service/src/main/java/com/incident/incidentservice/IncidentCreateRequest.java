package com.incident.incidentservice;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record IncidentCreateRequest(
        @NotBlank String title,
        String description,
        @NotBlank String serviceName,
        @NotBlank Incident.Severity severity,
        List<String> tags
) {
}