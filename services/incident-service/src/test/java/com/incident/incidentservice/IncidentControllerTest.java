package com.incident.incidentservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IncidentService incidentService;

    @Test
    void createIncident_returnsCreatedIncident() throws Exception {
        Incident incident = new Incident(
                "test-id-1", "DB connection lost", "Database failure",
                "payment-service", Incident.Severity.CRITICAL, Incident.Status.OPEN, 
                null, new ArrayList<>(), Instant.now(), Instant.now(), null, null
        );
        when(incidentService.createIncident(any(IncidentCreateRequest.class))).thenReturn(incident);

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "DB connection lost",
                                  "serviceName": "payment-service",
                                  "severity": "CRITICAL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-id-1"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"));
    }

    @Test
    void createIncident_withMissingTitle_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceName": "payment-service",
                                  "severity": "HIGH"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listIncidents_returnsEmptyList() throws Exception {
        when(incidentService.listIncidents(any(IncidentFilter.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listIncidents_returnsIncidentList() throws Exception {
        List<Incident> incidents = List.of(
                new Incident("id-1", "Latency spike", "High latency detected", "api-gateway", 
                        Incident.Severity.HIGH, Incident.Status.OPEN, null, new ArrayList<>(),
                        Instant.parse("2026-01-01T00:00:00Z"), Instant.now(), null, null),
                new Incident("id-2", "OOM error", "Memory exhausted", "worker-service", 
                        Incident.Severity.CRITICAL, Incident.Status.RESOLVED, null, new ArrayList<>(),
                        Instant.parse("2026-01-01T01:00:00Z"), Instant.now(), Instant.now(), null)
        );
        when(incidentService.listIncidents(any(IncidentFilter.class))).thenReturn(incidents);

        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("id-1"))
                .andExpect(jsonPath("$[1].status").value("RESOLVED"));
    }

    @Test
    void resolveIncident_returnsResolvedIncident() throws Exception {
        Incident resolved = new Incident(
                "id-1", "Latency spike", "Issue resolved", "api-gateway",
                Incident.Severity.HIGH, Incident.Status.RESOLVED, null, new ArrayList<>(),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.now(), Instant.now(), null
        );
        when(incidentService.resolveIncident("id-1")).thenReturn(resolved);

        mockMvc.perform(put("/api/v1/incidents/id-1/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void resolveIncident_whenNotFound_returnsNotFound() throws Exception {
        when(incidentService.resolveIncident("missing-id"))
                .thenThrow(new IncidentNotFoundException("missing-id"));

        mockMvc.perform(put("/api/v1/incidents/missing-id/resolve"))
                .andExpect(status().isNotFound());
    }
}