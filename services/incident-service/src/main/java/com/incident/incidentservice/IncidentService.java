package com.incident.incidentservice;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IncidentService {

    private final ConcurrentHashMap<UUID, Incident> incidentStore = new ConcurrentHashMap<>();
    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    public IncidentService(KafkaTemplate<String, Map<String, Object>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public Incident createIncident(IncidentCreateRequest request) {
        Incident incident = new Incident(
                UUID.randomUUID(),
                request.title(),
                request.serviceName(),
                request.severity(),
                "OPEN",
                Instant.now()
        );

        incidentStore.put(incident.id(), incident);
        publishIncidentOpenedEvent(incident);
        return incident;
    }

    public List<Incident> listIncidents() {
        return incidentStore.values().stream().toList();
    }

    private void publishIncidentOpenedEvent(Incident incident) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "incident-opened");
        event.put("schemaVersion", "v1");
        event.put("occurredAt", Instant.now().toString());
        event.put("incidentId", incident.id().toString());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", incident.title());
        payload.put("serviceName", incident.serviceName());
        payload.put("severity", incident.severity());
        payload.put("status", incident.status());
        payload.put("createdAt", incident.createdAt().toString());
        event.put("payload", payload);

        kafkaTemplate.send("incident.lifecycle.v1", incident.id().toString(), event);
        kafkaTemplate.send("incident.timeline.v1", incident.id().toString(), event);
    }
}
