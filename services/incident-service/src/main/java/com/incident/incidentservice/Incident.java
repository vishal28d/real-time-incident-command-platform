package com.incident.incidentservice;

import java.time.Instant;
import java.util.UUID;

public record Incident(
        UUID id,
        String title,
        String serviceName,
        String severity,
        String status,
        Instant createdAt
) {
}
