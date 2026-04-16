package com.incident.incidentservice;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository repository;

    @Mock
    private KafkaTemplate<String, Map<String, Object>> kafkaTemplate;

    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(repository, kafkaTemplate, new SimpleMeterRegistry());
    }

    @Test
    void createIncident_savesAndPublishesEvents() {
        IncidentCreateRequest request = new IncidentCreateRequest(
                "Redis down", "Redis unavailable", "cache-service", 
                Incident.Severity.HIGH, List.of("critical")
        );
        when(repository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident result = incidentService.createIncident(request);

        assertThat(result.getTitle()).isEqualTo("Redis down");
        assertThat(result.getServiceName()).isEqualTo("cache-service");
        assertThat(result.getSeverity()).isEqualTo(Incident.Severity.HIGH);
        assertThat(result.getStatus()).isEqualTo(Incident.Status.OPEN);
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCreatedAt()).isNotNull();

        verify(repository).save(any(Incident.class));
        verify(kafkaTemplate).send(eq("incident.lifecycle.v1"), any(), any());
        verify(kafkaTemplate).send(eq("incident.timeline.v1"), any(), any());
    }

    @Test
    void createIncident_publishedEventContainsExpectedFields() {
        IncidentCreateRequest request = new IncidentCreateRequest(
                "Redis down", "Redis unavailable", "cache-service", 
                Incident.Severity.HIGH, List.of("critical")
        );
        when(repository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        incidentService.createIncident(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate, times(2)).send(any(), any(), eventCaptor.capture());

        Map<String, Object> event = eventCaptor.getAllValues().get(0);
        assertThat(event).containsKey("eventId");
        assertThat(event.get("eventType")).isEqualTo("incident-opened");
        assertThat(event.get("schemaVersion")).isEqualTo("v1");
        assertThat(event).containsKey("incidentId");
        assertThat(event).containsKey("payload");
    }

    @Test
    void listIncidents_delegatesToRepository() {
        List<Incident> stored = List.of(
                new Incident("id-1", "Title", "Description", "svc", 
                        Incident.Severity.LOW, Incident.Status.OPEN, null, new ArrayList<>(),
                        Instant.now(), Instant.now(), null, null)
        );
        when(repository.findAll()).thenReturn(stored);

        List<Incident> result = incidentService.listIncidents(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("id-1");
    }

    @Test
    void resolveIncident_updatesStatusAndPublishesEvent() {
        Incident existing = new Incident("id-1", "Slow DB", "High latency", "db-service", 
                Incident.Severity.CRITICAL, Incident.Status.OPEN, null, new ArrayList<>(),
                Instant.now(), Instant.now(), null, null);
        when(repository.findById("id-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        Incident result = incidentService.resolveIncident("id-1");

        assertThat(result.getStatus()).isEqualTo(Incident.Status.RESOLVED);
        verify(kafkaTemplate).send(eq("incident.lifecycle.v1"), any(), any());
        verify(kafkaTemplate).send(eq("incident.timeline.v1"), any(), any());
    }

    @Test
    void resolveIncident_whenNotFound_throwsIncidentNotFoundException() {
        when(repository.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.resolveIncident("bad-id"))
                .isInstanceOf(IncidentNotFoundException.class)
                .hasMessageContaining("bad-id");
    }
}