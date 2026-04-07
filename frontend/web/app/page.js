"use client";

import { useEffect, useMemo, useState } from "react";

const incidentApi = process.env.NEXT_PUBLIC_INCIDENT_API || "http://localhost:8082";
const realtimeApi = process.env.NEXT_PUBLIC_REALTIME_API || "http://localhost:8083";

export default function Page() {
  const [incidents, setIncidents] = useState([]);
  const [events, setEvents] = useState([]);

  useEffect(() => {
    fetch(`${incidentApi}/api/v1/incidents`)
      .then((response) => response.json())
      .then((data) => setIncidents(Array.isArray(data) ? data : []))
      .catch(() => setIncidents([]));
  }, []);

  useEffect(() => {
    const source = new EventSource(`${realtimeApi}/api/v1/stream/events`);
    source.addEventListener("incident-event", (message) => {
      try {
        const parsed = JSON.parse(message.data);
        setEvents((previous) => [parsed, ...previous].slice(0, 25));
      } catch {
      }
    });

    return () => {
      source.close();
    };
  }, []);

  const openCount = useMemo(() => incidents.filter((incident) => incident.status === "OPEN").length, [incidents]);

  return (
    <main style={{ padding: "24px", maxWidth: "1200px", margin: "0 auto" }}>
      <h1 style={{ marginTop: 0 }}>Incident Command Center</h1>
      <p style={{ opacity: 0.8 }}>MVP live board for on-call teams</p>

      <section style={{ display: "grid", gridTemplateColumns: "repeat(3, minmax(0, 1fr))", gap: "12px", marginBottom: "20px" }}>
        <MetricCard title="Total Incidents" value={incidents.length} />
        <MetricCard title="Open Incidents" value={openCount} />
        <MetricCard title="Live Events" value={events.length} />
      </section>

      <section style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "14px" }}>
        <Panel title="Active Incidents">
          {incidents.length === 0 && <div>No incidents yet</div>}
          {incidents.map((incident) => (
            <div key={incident.id} style={{ borderBottom: "1px solid #1c2640", padding: "10px 0" }}>
              <div style={{ fontWeight: 600 }}>{incident.title}</div>
              <div style={{ opacity: 0.75, fontSize: "14px" }}>{incident.serviceName} | {incident.severity} | {incident.status}</div>
            </div>
          ))}
        </Panel>

        <Panel title="Live Timeline">
          {events.length === 0 && <div>No events yet</div>}
          {events.map((event) => (
            <div key={event.eventId} style={{ borderBottom: "1px solid #1c2640", padding: "10px 0" }}>
              <div style={{ fontWeight: 600 }}>{event.eventType}</div>
              <div style={{ opacity: 0.75, fontSize: "14px" }}>{event.incidentId}</div>
              <div style={{ opacity: 0.65, fontSize: "12px" }}>{event.occurredAt}</div>
            </div>
          ))}
        </Panel>
      </section>
    </main>
  );
}

function MetricCard({ title, value }) {
  return (
    <div style={{ background: "#141c33", border: "1px solid #1c2640", borderRadius: "10px", padding: "14px" }}>
      <div style={{ opacity: 0.75, fontSize: "14px" }}>{title}</div>
      <div style={{ marginTop: "8px", fontSize: "28px", fontWeight: 700 }}>{value}</div>
    </div>
  );
}

function Panel({ title, children }) {
  return (
    <div style={{ background: "#141c33", border: "1px solid #1c2640", borderRadius: "10px", padding: "14px", minHeight: "400px" }}>
      <h3 style={{ marginTop: 0 }}>{title}</h3>
      {children}
    </div>
  );
}
