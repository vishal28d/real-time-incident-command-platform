"use client";

import { useEffect, useMemo, useState } from "react";

const incidentApi = process.env.NEXT_PUBLIC_INCIDENT_API || "http://localhost:8082";
const realtimeApi = process.env.NEXT_PUBLIC_REALTIME_API || "http://localhost:8083";

export default function Page() {
  const [incidents, setIncidents] = useState([]);
  const [events, setEvents] = useState([]);
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const [assignModal, setAssignModal] = useState(null); // incidentId
  const [assignInput, setAssignInput] = useState("");

  // Initial fetch
  useEffect(() => {
    fetch(`${incidentApi}/api/v1/incidents`)
      .then((r) => r.json())
      .then((data) => {
        setIncidents(Array.isArray(data) ? data : []);
        setLoading(false);
      })
      .catch(() => {
        setIncidents([]);
        setLoading(false);
      });
  }, []);

  // SSE connection
  useEffect(() => {
    let source;

    function connect() {
      source = new EventSource(`${realtimeApi}/api/v1/stream/events`);

      source.addEventListener("open", () => setConnected(true));

      const handleMessage = (message) => {
        try {
          const parsed = JSON.parse(message.data);
          setEvents((prev) => [parsed, ...prev].slice(0, 100));

          if (
            (parsed.eventType === "incident-opened" ||
              parsed.eventType === "alert-correlated") &&
            parsed.payload
          ) {
            const incoming = {
              id: parsed.incidentId,
              title: parsed.payload.title,
              serviceName: parsed.payload.serviceName,
              severity: parsed.payload.severity,
              status: parsed.payload.status,
              assignedTo: parsed.payload.assignedTo,
              tags: parsed.payload.tags,
              createdAt: parsed.payload.createdAt,
            };
            setIncidents((prev) => {
              const exists = prev.some((i) => i.id === incoming.id);
              if (exists) {
                return [incoming, ...prev.filter((i) => i.id !== incoming.id)];
              }
              return [incoming, ...prev];
            });
          }

          if (parsed.eventType === "incident-resolved") {
            setIncidents((prev) =>
              prev.map((i) =>
                i.id === parsed.incidentId ? { ...i, status: "RESOLVED" } : i
              )
            );
          }

          if (parsed.eventType === "incident-assigned" && parsed.payload) {
            setIncidents((prev) =>
              prev.map((i) =>
                i.id === parsed.incidentId
                  ? { ...i, assignedTo: parsed.payload.assignedTo }
                  : i
              )
            );
          }
        } catch (err) {
          console.error("SSE parse error:", err);
        }
      };

      source.addEventListener("incident-event", handleMessage);
      source.onmessage = handleMessage;

      source.onerror = () => {
        setConnected(false);
        source.close();
        setTimeout(connect, 3000);
      };
    }

    connect();
    return () => {
      if (source) source.close();
    };
  }, []);

  function resolveIncident(id) {
    fetch(`${incidentApi}/api/v1/incidents/${id}/resolve`, { method: "PUT" })
      .then((r) => r.json())
      .then((updated) =>
        setIncidents((prev) => prev.map((i) => (i.id === updated.id ? updated : i)))
      )
      .catch(() => {});
  }

  function assignIncident(id, assignedTo) {
    fetch(`${incidentApi}/api/v1/incidents/${id}/assign?assignedTo=${encodeURIComponent(assignedTo)}`, {
      method: "PUT",
    })
      .then((r) => r.json())
      .then((updated) =>
        setIncidents((prev) => prev.map((i) => (i.id === updated.id ? updated : i)))
      )
      .catch(() => {})
      .finally(() => {
        setAssignModal(null);
        setAssignInput("");
      });
  }

  const openCount = useMemo(() => incidents.filter((i) => i.status === "OPEN").length, [incidents]);
  const resolvedCount = useMemo(() => incidents.filter((i) => i.status === "RESOLVED").length, [incidents]);

  const filteredIncidents = useMemo(() => {
    let list = incidents;
    if (statusFilter !== "ALL") {
      list = list.filter((i) => i.status === statusFilter);
    }
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      list = list.filter(
        (i) =>
          i.title?.toLowerCase().includes(q) ||
          i.serviceName?.toLowerCase().includes(q)
      );
    }
    return list;
  }, [incidents, statusFilter, searchQuery]);

  return (
    <main
      style={{
        padding: "24px",
        maxWidth: "1400px",
        margin: "0 auto",
        fontFamily: "'Inter', system-ui, sans-serif",
        color: "#e2e8f0",
        minHeight: "100vh",
      }}
    >
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "24px" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: "22px", fontWeight: 700, letterSpacing: "-0.3px" }}>
            Incident Command Center
          </h1>
          <p style={{ margin: "4px 0 0", opacity: 0.5, fontSize: "13px" }}>
            Real-time incident tracking for on-call teams
          </p>
        </div>
        <ConnectionBadge connected={connected} />
      </div>

      {/* Metric Cards */}
      <section
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4, minmax(0, 1fr))",
          gap: "12px",
          marginBottom: "24px",
        }}
      >
        <MetricCard title="Total Incidents" value={loading ? "—" : incidents.length} />
        <MetricCard title="Open" value={loading ? "—" : openCount} color="#f87171" />
        <MetricCard title="Resolved" value={loading ? "—" : resolvedCount} color="#4ade80" />
        <MetricCard title="Live Events" value={events.length} color="#60a5fa" />
      </section>

      {/* Main Grid */}
      <section style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px" }}>
        {/* Incidents Panel */}
        <Panel
          title="Active Incidents"
          toolbar={
            <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
              {/* Filter tabs */}
              <div style={{ display: "flex", gap: "4px" }}>
                {["ALL", "OPEN", "RESOLVED"].map((f) => (
                  <button
                    key={f}
                    onClick={() => setStatusFilter(f)}
                    style={{
                      background: statusFilter === f ? "#1e40af" : "transparent",
                      border: `1px solid ${statusFilter === f ? "#3b82f6" : "#1e293b"}`,
                      color: statusFilter === f ? "#93c5fd" : "#64748b",
                      borderRadius: "5px",
                      padding: "3px 9px",
                      fontSize: "11px",
                      fontWeight: 600,
                      cursor: "pointer",
                      textTransform: "uppercase",
                      letterSpacing: "0.5px",
                    }}
                  >
                    {f}
                  </button>
                ))}
              </div>
              {/* Search */}
              <input
                type="text"
                placeholder="Search…"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{
                  background: "#0b1320",
                  border: "1px solid #1e293b",
                  borderRadius: "5px",
                  color: "#e2e8f0",
                  fontSize: "12px",
                  padding: "3px 9px",
                  outline: "none",
                  width: "110px",
                }}
              />
            </div>
          }
        >
          {loading && <LoadingSkeleton />}
          {!loading && filteredIncidents.length === 0 && (
            <Empty>
              {searchQuery || statusFilter !== "ALL"
                ? "No incidents match the filter"
                : "No incidents yet — send an alert to get started"}
            </Empty>
          )}
          {!loading &&
            filteredIncidents.map((incident) => (
              <IncidentRow
                key={incident.id}
                incident={incident}
                onResolve={resolveIncident}
                onAssign={(id) => {
                  setAssignModal(id);
                  setAssignInput(incident.assignedTo || "");
                }}
              />
            ))}
        </Panel>

        {/* Timeline Panel */}
        <Panel title="Live Timeline">
          {events.length === 0 && (
            <Empty>Waiting for events… try sending an alert</Empty>
          )}
          {events.map((event) => (
            <TimelineRow key={event.eventId} event={event} />
          ))}
        </Panel>
      </section>

      {/* Assign Modal */}
      {assignModal && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.6)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 100,
          }}
          onClick={() => setAssignModal(null)}
        >
          <div
            style={{
              background: "#0f172a",
              border: "1px solid #1e293b",
              borderRadius: "10px",
              padding: "20px",
              width: "320px",
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ margin: "0 0 12px", fontSize: "15px" }}>Assign Incident</h3>
            <input
              autoFocus
              type="text"
              placeholder="Enter name or email…"
              value={assignInput}
              onChange={(e) => setAssignInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && assignInput.trim()) {
                  assignIncident(assignModal, assignInput.trim());
                }
              }}
              style={{
                width: "100%",
                boxSizing: "border-box",
                background: "#0b1320",
                border: "1px solid #334155",
                borderRadius: "6px",
                color: "#e2e8f0",
                fontSize: "13px",
                padding: "8px 10px",
                outline: "none",
                marginBottom: "12px",
              }}
            />
            <div style={{ display: "flex", gap: "8px", justifyContent: "flex-end" }}>
              <button
                onClick={() => setAssignModal(null)}
                style={{
                  background: "transparent",
                  border: "1px solid #334155",
                  color: "#94a3b8",
                  borderRadius: "6px",
                  padding: "6px 14px",
                  fontSize: "12px",
                  cursor: "pointer",
                }}
              >
                Cancel
              </button>
              <button
                onClick={() => assignInput.trim() && assignIncident(assignModal, assignInput.trim())}
                style={{
                  background: "#1e3a5f",
                  border: "1px solid #3b82f6",
                  color: "#93c5fd",
                  borderRadius: "6px",
                  padding: "6px 14px",
                  fontSize: "12px",
                  cursor: "pointer",
                }}
              >
                Assign
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function ConnectionBadge({ connected }) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: "6px",
        background: "#0f172a",
        border: `1px solid ${connected ? "#166534" : "#7f1d1d"}`,
        borderRadius: "20px",
        padding: "5px 12px",
        fontSize: "12px",
        color: connected ? "#4ade80" : "#f87171",
        flexShrink: 0,
      }}
    >
      <span
        style={{
          width: "7px",
          height: "7px",
          borderRadius: "50%",
          background: connected ? "#4ade80" : "#f87171",
          boxShadow: connected ? "0 0 6px #4ade80" : "none",
          animation: connected ? "pulse 2s infinite" : "none",
          display: "inline-block",
        }}
      />
      {connected ? "Live" : "Reconnecting…"}
      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.4; }
        }
      `}</style>
    </div>
  );
}

function IncidentRow({ incident, onResolve, onAssign }) {
  const isOpen = incident.status === "OPEN";
  return (
    <div
      style={{
        borderBottom: "1px solid #1e293b",
        padding: "12px 0",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: "10px",
      }}
    >
      <div style={{ flex: 1, minWidth: 0 }}>
        {/* Title */}
        <div
          style={{
            fontWeight: 600,
            fontSize: "13px",
            marginBottom: "3px",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {incident.title}
        </div>

        {/* Service + time */}
        <div style={{ fontSize: "12px", opacity: 0.6, marginBottom: "6px" }}>
          {incident.serviceName}
          {incident.createdAt && (
            <span style={{ marginLeft: "8px", opacity: 0.7 }}>
              · {relativeTime(incident.createdAt)}
            </span>
          )}
        </div>

        {/* Badges row */}
        <div style={{ display: "flex", gap: "5px", flexWrap: "wrap", alignItems: "center" }}>
          <Badge color={severityColor(incident.severity)}>{incident.severity}</Badge>
          <Badge color={statusColor(incident.status)}>{incident.status}</Badge>
          {incident.tags?.map((tag) => (
            <Badge key={tag} color="#7c3aed">#{tag}</Badge>
          ))}
          {incident.assignedTo && (
            <span
              style={{
                fontSize: "11px",
                color: "#94a3b8",
                background: "#1e293b",
                borderRadius: "4px",
                padding: "1px 6px",
              }}
            >
              👤 {incident.assignedTo}
            </span>
          )}
        </div>
      </div>

      {/* Action buttons */}
      {isOpen && (
        <div style={{ display: "flex", flexDirection: "column", gap: "5px", flexShrink: 0 }}>
          <ActionButton color="#4ade80" borderColor="#166534" bg="#1e3a2f" onClick={() => onResolve(incident.id)}>
            Resolve
          </ActionButton>
          <ActionButton color="#93c5fd" borderColor="#1e40af" bg="#1e3a5f" onClick={() => onAssign(incident.id)}>
            Assign
          </ActionButton>
        </div>
      )}
    </div>
  );
}

function TimelineRow({ event }) {
  return (
    <div style={{ borderBottom: "1px solid #1e293b", padding: "10px 0" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span style={{ fontWeight: 600, fontSize: "13px", display: "flex", alignItems: "center", gap: "6px" }}>
          <EventTypeDot type={event.eventType} />
          {event.eventType}
        </span>
        <span style={{ opacity: 0.45, fontSize: "11px" }}>{formatTime(event.occurredAt)}</span>
      </div>
      {event.payload?.serviceName && (
        <div style={{ opacity: 0.65, fontSize: "12px", marginTop: "3px" }}>
          {event.payload.serviceName}
          {event.payload.severity && (
            <span
              style={{
                marginLeft: "6px",
                color: severityColor(event.payload.severity),
                fontWeight: 600,
              }}
            >
              {event.payload.severity}
            </span>
          )}
        </div>
      )}
      <div
        style={{
          opacity: 0.3,
          fontSize: "10px",
          marginTop: "2px",
          fontFamily: "monospace",
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
        }}
      >
        {event.incidentId}
      </div>
    </div>
  );
}

function EventTypeDot({ type }) {
  const colors = {
    "alert-received": "#facc15",
    "alert-correlated": "#fb923c",
    "incident-opened": "#f87171",
    "incident-resolved": "#4ade80",
    "incident-assigned": "#60a5fa",
  };
  return (
    <span
      style={{
        display: "inline-block",
        width: "8px",
        height: "8px",
        borderRadius: "50%",
        background: colors[type] || "#94a3b8",
        flexShrink: 0,
      }}
    />
  );
}

function MetricCard({ title, value, color = "#e2e8f0" }) {
  return (
    <div
      style={{
        background: "#0f172a",
        border: "1px solid #1e293b",
        borderRadius: "10px",
        padding: "16px",
      }}
    >
      <div style={{ opacity: 0.5, fontSize: "12px", fontWeight: 500, textTransform: "uppercase", letterSpacing: "0.5px" }}>
        {title}
      </div>
      <div
        style={{
          marginTop: "8px",
          fontSize: "30px",
          fontWeight: 700,
          color,
          fontVariantNumeric: "tabular-nums",
        }}
      >
        {value}
      </div>
    </div>
  );
}

function Panel({ title, children, toolbar }) {
  return (
    <div
      style={{
        background: "#0f172a",
        border: "1px solid #1e293b",
        borderRadius: "10px",
        padding: "16px",
        minHeight: "460px",
        display: "flex",
        flexDirection: "column",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: "12px",
          gap: "8px",
          flexWrap: "wrap",
        }}
      >
        <h3 style={{ margin: 0, fontSize: "14px", fontWeight: 600 }}>{title}</h3>
        {toolbar}
      </div>
      <div style={{ flex: 1, overflowY: "auto" }}>{children}</div>
    </div>
  );
}

function Badge({ color, children }) {
  return (
    <span
      style={{
        background: color + "20",
        border: `1px solid ${color}50`,
        color,
        borderRadius: "4px",
        padding: "1px 6px",
        fontSize: "11px",
        fontWeight: 600,
        whiteSpace: "nowrap",
      }}
    >
      {children}
    </span>
  );
}

function ActionButton({ color, borderColor, bg, onClick, children }) {
  return (
    <button
      onClick={onClick}
      style={{
        background: bg,
        border: `1px solid ${borderColor}`,
        color,
        borderRadius: "5px",
        padding: "3px 10px",
        fontSize: "11px",
        fontWeight: 600,
        cursor: "pointer",
        whiteSpace: "nowrap",
      }}
    >
      {children}
    </button>
  );
}

function Empty({ children }) {
  return (
    <div
      style={{
        opacity: 0.35,
        fontSize: "13px",
        marginTop: "24px",
        textAlign: "center",
        lineHeight: 1.6,
      }}
    >
      {children}
    </div>
  );
}

function LoadingSkeleton() {
  return (
    <div>
      {[1, 2, 3].map((i) => (
        <div
          key={i}
          style={{
            borderBottom: "1px solid #1e293b",
            padding: "12px 0",
            opacity: 0.4,
          }}
        >
          <div
            style={{
              height: "13px",
              width: "60%",
              background: "#1e293b",
              borderRadius: "4px",
              marginBottom: "8px",
            }}
          />
          <div
            style={{
              height: "11px",
              width: "30%",
              background: "#1e293b",
              borderRadius: "4px",
            }}
          />
        </div>
      ))}
    </div>
  );
}

// ── Helpers ────────────────────────────────────────────────────────────────────

function severityColor(s) {
  if (!s) return "#94a3b8";
  switch (s.toUpperCase()) {
    case "CRITICAL": return "#f87171";
    case "HIGH":     return "#fb923c";
    case "MEDIUM":   return "#facc15";
    case "LOW":      return "#4ade80";
    default:         return "#94a3b8";
  }
}

function statusColor(s) {
  if (!s) return "#94a3b8";
  switch (s.toUpperCase()) {
    case "OPEN":         return "#f87171";
    case "INVESTIGATING":return "#facc15";
    case "RESOLVED":     return "#4ade80";
    case "CLOSED":       return "#64748b";
    default:             return "#94a3b8";
  }
}

function formatTime(iso) {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  } catch {
    return iso;
  }
}

function relativeTime(iso) {
  if (!iso) return "";
  try {
    const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
    if (diff < 60)  return `${diff}s ago`;
    if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
    if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
    return `${Math.floor(diff / 86400)}d ago`;
  } catch {
    return "";
  }
}
