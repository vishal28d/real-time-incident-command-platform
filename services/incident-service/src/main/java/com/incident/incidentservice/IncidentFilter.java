package com.incident.incidentservice;

import java.util.List;

public record IncidentFilter(
        Incident.Severity severity,
        Incident.Status status,
        String serviceName,
        String assignedTo,
        String tag,
        List<String> tags
) {
    public static IncidentFilter of(Incident.Severity severity, Incident.Status status, 
                                   String serviceName, String assignedTo, String tag) {
        return new IncidentFilter(severity, status, serviceName, assignedTo, tag, null);
    }
}