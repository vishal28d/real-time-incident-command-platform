package com.incident.incidentservice;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "incident_comments")
public class IncidentComment {

    @Id
    private String id;
    private String incidentId;
    private String author;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    public IncidentComment() {}

    public IncidentComment(String id, String incidentId, String author, String content, 
                           Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.author = author;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getIncidentId() { return incidentId; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(String id) { this.id = id; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }
    public void setAuthor(String author) { this.author = author; }
    public void setContent(String content) { this.content = content; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}