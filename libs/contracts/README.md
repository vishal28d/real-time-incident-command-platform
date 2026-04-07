# Event Contracts

This folder stores shared event contracts for producers and consumers.

## Baseline Envelope

- `eventId`: unique event identifier
- `eventType`: business event name
- `schemaVersion`: schema version (`v1`)
- `occurredAt`: UTC timestamp
- `traceId`: optional distributed-trace identifier
- `incidentId`: aggregate key
- `payload`: event-specific object

## Topics

- `alert.received.v1`
- `incident.lifecycle.v1`
- `incident.timeline.v1`
