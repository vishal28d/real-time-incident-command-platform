package com.incident.incidentservice;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends MongoRepository<Incident, String> {
    Optional<Incident> findByServiceNameAndStatus(String serviceName, Incident.Status status);
    List<Incident> findByAssignedTo(String assignedTo);
    List<Incident> findByTagsContaining(String tag);
}