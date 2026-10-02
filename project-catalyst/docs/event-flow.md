# Event flow

```mermaid
sequenceDiagram
  participant C as Customer UI
  participant A as API
  participant B as Event bus
  participant D as Decision engine
  participant G as AI agent
  C->>A: POST liquidity event (idempotency key)
  A->>B: liquidity-events
  B->>D: claim event (RECEIVED→PROCESSING)
  D->>D: enrich context, evaluate rules
  D->>G: minimal context + eligible options
  G-->>D: ranking + explanation (validated)
  D->>B: recommendations
  C->>A: view, then approve (confirmed=true)
  A->>B: financial-actions
  B->>A: execute only with valid consent
  A->>C: progress + audit timeline
```

Correlation IDs (`COR-…`) and event IDs are in every log line and audit row.
Idempotency: unique idempotency key on ingest; consumers atomically claim the event; replays are audited as `DUPLICATE_EVENT_DETECTED`; approval and execution are guarded state transitions.