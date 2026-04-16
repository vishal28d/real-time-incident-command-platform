package com.incident.incidentservice;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "incidents")
public class Incident {

    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW
    }

    public enum Status {
        OPEN, INVESTIGATING, RESOLVED, CLOSED
    }

    @Id
    private String id;
    private String title;
    private String description;
    private String serviceName;
    private Severity severity;
    private Status status;
    private String assignedTo;
    private List<String> tags;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant resolvedAt;
    private Instant closedAt;

    public Incident() {}

    public Incident(String id, String title, String description, String serviceName, 
                    Severity severity, Status status, String assignedTo, List<String> tags,
                    Instant createdAt, Instant updatedAt, Instant resolvedAt, Instant closedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.serviceName = serviceName;
        this.severity = severity;
        this.status = status;
        this.assignedTo = assignedTo;
        this.tags = tags;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.resolvedAt = resolvedAt;
        this.closedAt = closedAt;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getServiceName() { return serviceName; }
    public Severity getSeverity() { return severity; }
    public Status getStatus() { return status; }
    public String getAssignedTo() { return assignedTo; }
    public List<String> getTags() { return tags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getClosedAt() { return closedAt; }

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public void setSeverity(Severity severity) { this.severity = severity; }
    public void setStatus(Status status) { this.status = status; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
}