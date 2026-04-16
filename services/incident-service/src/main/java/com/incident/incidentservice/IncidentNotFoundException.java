package com.incident.incidentservice;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class IncidentNotFoundException extends RuntimeException {
    public IncidentNotFoundException(String id) {
        super("Incident not found: " + id);
    }
}
