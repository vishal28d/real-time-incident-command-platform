package com.incident.incidentservice;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IncidentService {

    private final IncidentRepository repository;
    private final KafkaTemplate<String, Map<String, Object>> kafkaTemplate;
    private final Counter incidentCreatedCounter;
    private final Counter incidentResolvedCounter;

    public IncidentService(IncidentRepository repository, 
                         KafkaTemplate<String, Map<String, Object>> kafkaTemplate,
                         MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.incidentCreatedCounter = Counter.builder("incident.created.count")
                .description("Number of incidents created")
                .register(meterRegistry);
        this.incidentResolvedCounter = Counter.builder("incident.resolved.count")
                .description("Number of incidents resolved")
                .register(meterRegistry);
    }

    @CircuitBreaker(name = "mongoDB", fallbackMethod = "createIncidentFallback")
    public Incident createIncident(IncidentCreateRequest request) {
        Optional<Incident> existing = repository.findByServiceNameAndStatus(request.serviceName(), Incident.Status.OPEN);
        if (existing.isPresent()) {
            Incident incident = existing.get();
            publishEvent("alert-correlated", incident, request.description());
            return incident;
        }

        Instant now = Instant.now();
        Incident incident = new Incident(
                UUID.randomUUID().toString(),
                request.title(),
                request.description(),
                request.serviceName(),
                request.severity(),
                Incident.Status.OPEN,
                null,
                request.tags() != null ? request.tags() : new ArrayList<>(),
                now,
                now,
                null,
                null
        );
        repository.save(incident);
        incidentCreatedCounter.increment();
        publishEvent("incident-opened", incident, "Incident created");
        return incident;
    }

    @SuppressWarnings("unused")
    private Incident createIncidentFallback(IncidentCreateRequest request, Throwable t) {
        throw new RuntimeException("Service temporarily unavailable. Please try again later.");
    }

    public List<Incident> listIncidents(IncidentFilter filter) {
        List<Incident> incidents = repository.findAll();
        
        if (filter == null) {
            return incidents;
        }
        
        return incidents.stream()
                .filter(i -> filter.severity() == null || i.getSeverity() == filter.severity())
                .filter(i -> filter.status() == null || i.getStatus() == filter.status())
                .filter(i -> filter.serviceName() == null || i.getServiceName().equals(filter.serviceName()))
                .filter(i -> filter.assignedTo() == null || 
                        (i.getAssignedTo() != null && i.getAssignedTo().equals(filter.assignedTo())))
                .filter(i -> filter.tag() == null || 
                        (i.getTags() != null && i.getTags().contains(filter.tag())))
                .collect(Collectors.toList());
    }

    public List<Incident> searchIncidents(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAll();
        }
        String lowerQuery = query.toLowerCase();
        return repository.findAll().stream()
                .filter(i -> i.getTitle() != null && i.getTitle().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }

    @CircuitBreaker(name = "mongoDB")
    public Incident resolveIncident(String id) {
        Incident incident = repository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
        incident.setStatus(Incident.Status.RESOLVED);
        incident.setResolvedAt(Instant.now());
        incident.setUpdatedAt(Instant.now());
        repository.save(incident);
        incidentResolvedCounter.increment();
        publishEvent("incident-resolved", incident, "Incident resolved");
        return incident;
    }

    @CircuitBreaker(name = "mongoDB")
    public Incident assignIncident(String id, String assignedTo) {
        Incident incident = repository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
        incident.setAssignedTo(assignedTo);
        incident.setUpdatedAt(Instant.now());
        repository.save(incident);
        publishEvent("incident-assigned", incident, "Assigned to " + assignedTo);
        return incident;
    }

    @CircuitBreaker(name = "mongoDB")
    public Incident addTag(String id, String tag) {
        Incident incident = repository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
        if (incident.getTags() == null) {
            incident.setTags(new ArrayList<>());
        }
        if (!incident.getTags().contains(tag)) {
            incident.getTags().add(tag);
        }
        incident.setUpdatedAt(Instant.now());
        repository.save(incident);
        return incident;
    }

    @CircuitBreaker(name = "mongoDB")
    public Incident removeTag(String id, String tag) {
        Incident incident = repository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
        if (incident.getTags() != null) {
            incident.getTags().remove(tag);
        }
        incident.setUpdatedAt(Instant.now());
        repository.save(incident);
        return incident;
    }

    private void publishEvent(String eventType, Incident incident, String description) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("schemaVersion", "v1");
        event.put("occurredAt", Instant.now().toString());
        event.put("incidentId", incident.getId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", incident.getTitle());
        payload.put("description", incident.getDescription());
        payload.put("serviceName", incident.getServiceName());
        payload.put("severity", incident.getSeverity());
        payload.put("status", incident.getStatus());
        payload.put("assignedTo", incident.getAssignedTo());
        payload.put("tags", incident.getTags());
        payload.put("createdAt", incident.getCreatedAt() != null ? incident.getCreatedAt().toString() : null);
        event.put("payload", payload);

        kafkaTemplate.send("incident.lifecycle.v1", incident.getId(), event);
        kafkaTemplate.send("incident.timeline.v1", incident.getId(), event);
    }
}