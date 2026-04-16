package com.incident.incidentservice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incidents", description = "Incident management endpoints")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping
    @Operation(summary = "Create incident", description = "Create a new incident alert")
    public Incident createIncident(@Valid @RequestBody IncidentCreateRequest request) {
        return incidentService.createIncident(request);
    }

    @GetMapping
    @Operation(summary = "List incidents", description = "Get all incidents with optional filters")
    public List<Incident> listIncidents(
            @RequestParam(required = false) Incident.Severity severity,
            @RequestParam(required = false) Incident.Status status,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String query) {
        if (query != null && !query.isBlank()) {
            return incidentService.searchIncidents(query);
        }
        IncidentFilter filter = IncidentFilter.of(severity, status, serviceName, assignedTo, tag);
        return incidentService.listIncidents(filter);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get incident", description = "Get incident by ID")
    public ResponseEntity<Incident> getIncident(@PathVariable String id) {
        return incidentService.listIncidents(null).stream()
                .filter(i -> i.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/resolve")
    @Operation(summary = "Resolve incident", description = "Mark incident as resolved")
    public Incident resolveIncident(@PathVariable String id) {
        return incidentService.resolveIncident(id);
    }

    @PutMapping("/{id}/assign")
    @Operation(summary = "Assign incident", description = "Assign incident to a user")
    public Incident assignIncident(
            @PathVariable String id,
            @RequestParam String assignedTo) {
        return incidentService.assignIncident(id, assignedTo);
    }

    @PutMapping("/{id}/tags")
    @Operation(summary = "Add tag", description = "Add tag to incident")
    public Incident addTag(
            @PathVariable String id,
            @RequestParam String tag) {
        return incidentService.addTag(id, tag);
    }

    @DeleteMapping("/{id}/tags")
    @Operation(summary = "Remove tag", description = "Remove tag from incident")
    public Incident removeTag(
            @PathVariable String id,
            @RequestParam String tag) {
        return incidentService.removeTag(id, tag);
    }
}