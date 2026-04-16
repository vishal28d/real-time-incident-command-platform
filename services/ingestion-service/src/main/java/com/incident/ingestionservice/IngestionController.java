package com.incident.ingestionservice;

import jakarta.validation.Valid;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class IngestionController {

    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public IngestionController(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping
    public Map<String, String> ingest(@Valid @RequestBody AlertRequest request) {
        String incidentKey = UUID.randomUUID().toString();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "alert-received");
        event.put("schemaVersion", "v1");
        event.put("occurredAt", Instant.now().toString());
        event.put("incidentId", incidentKey);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceName", request.serviceName());
        payload.put("severity", request.severity());
        payload.put("message", request.message());
        event.put("payload", payload);

        kafkaTemplate.send("alert.received.v1", incidentKey, event);
        kafkaTemplate.send("incident.timeline.v1", incidentKey, event);

        return Map.of("status", "accepted", "trackingId", incidentKey);
    }

    @PostMapping("/prometheus")
    public Map<String, String> ingestPrometheus(@Valid @RequestBody PrometheusAlertRequest request) {
        String incidentKey = UUID.randomUUID().toString();

        // Process each alert in the Prometheus webhook
        for (PrometheusAlert alert : request.alerts()) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("eventType", "alert-received");
            event.put("schemaVersion", "v1");
            event.put("occurredAt", Instant.now().toString());
            event.put("incidentId", incidentKey);

            Map<String, Object> payload = new LinkedHashMap<>();
            // Extract service name from labels (common pattern)
            String serviceName = alert.labels().getOrDefault("service", "unknown-service");
            String severity = alert.labels().getOrDefault("severity", "UNKNOWN");
            // Use summary annotation as message, fallback to description
            String message = alert.annotations().getOrDefault("summary",
                alert.annotations().getOrDefault("description", "Prometheus alert"));

            payload.put("serviceName", serviceName);
            payload.put("severity", mapSeverity(severity));
            payload.put("message", message);
            payload.put("source", "prometheus");
            payload.put("alertName", alert.labels().get("alertname"));
            payload.put("generatorURL", alert.generatorURL());

            event.put("payload", payload);

            kafkaTemplate.send("alert.received.v1", incidentKey, event);
            kafkaTemplate.send("incident.timeline.v1", incidentKey, event);
        }

        return Map.of("status", "accepted", "trackingId", incidentKey);
    }

    private String mapSeverity(String prometheusSeverity) {
        return switch (prometheusSeverity.toLowerCase()) {
            case "critical" -> "CRITICAL";
            case "warning" -> "HIGH";
            case "info" -> "MEDIUM";
            default -> "LOW";
        };
    }
}