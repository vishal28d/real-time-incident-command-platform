package com.incident.incidentservice;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AlertConsumer {

    private final IncidentService incidentService;

    public AlertConsumer(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @KafkaListener(topics = "alert.received.v1", groupId = "incident-service")
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "onAlertReceivedFallback")
    public void onAlertReceived(Map<String, Object> event) {
        Object payloadObj = event.get("payload");
        if (!(payloadObj instanceof Map<?, ?> rawPayload)) {
            return;
        }

        String serviceName = (String) rawPayload.get("serviceName");
        String severityStr = (String) rawPayload.get("severity");
        String message = (String) rawPayload.get("message");

        if (serviceName == null || severityStr == null || message == null) {
            return;
        }

        Incident.Severity severity;
        try {
            severity = Incident.Severity.valueOf(severityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            severity = Incident.Severity.MEDIUM;
        }

        IncidentCreateRequest request = new IncidentCreateRequest(
                message,
                message,
                serviceName,
                severity,
                List.of("auto-created")
        );
        incidentService.createIncident(request);
    }

    @SuppressWarnings("unused")
    private void onAlertReceivedFallback(Map<String, Object> event, Throwable t) {
        System.err.println("Circuit breaker triggered for alert processing: " + t.getMessage());
    }
}