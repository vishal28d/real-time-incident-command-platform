package com.incident.incidentservice;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "incident_timeline")
public class IncidentTimeline {

    @Id
    private String id;
    private String incidentId;
    private String eventType;
    private String description;
    private String performedBy;
    private Instant occurredAt;
    private Instant createdAt;

    public IncidentTimeline() {}

    public IncidentTimeline(String id, String incidentId, String eventType, String description, 
                          String performedBy, Instant occurredAt, Instant createdAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.eventType = eventType;
        this.description = description;
        this.performedBy = performedBy;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getIncidentId() { return incidentId; }
    public String getEventType() { return eventType; }
    public String getDescription() { return description; }
    public String getPerformedBy() { return performedBy; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(String id) { this.id = id; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setDescription(String description) { this.description = description; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}