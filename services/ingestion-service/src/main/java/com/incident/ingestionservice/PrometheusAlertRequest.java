package com.incident.ingestionservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record PrometheusAlertRequest(
        @NotBlank String version,
        @NotBlank String groupKey,
        @NotBlank String status,
        @NotBlank String receiver,
        @NotNull Map<String, String> groupLabels,
        @NotNull Map<String, String> commonLabels,
        @NotNull Map<String, String> commonAnnotations,
        @NotBlank String externalURL,
        @NotEmpty List<PrometheusAlert> alerts
) {
}

record PrometheusAlert(
        @NotBlank String status,
        @NotNull Map<String, String> labels,
        @NotNull Map<String, String> annotations,
        @NotBlank String startsAt,
        String endsAt,
        String generatorURL
) {
}