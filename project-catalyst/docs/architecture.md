# Architecture

Modular monolith; each package could become a service (they communicate via the `EventBus` and repositories only).

```mermaid
flowchart LR
  UI[React UI] -->|POST /api/demo/liquidity-events| API
  API --> LES[LiquidityEventService<br/>idempotent ingest]
  LES -->|liquidity-events| BUS[(EventBus: Kafka or in-memory)]
  BUS --> EP[EventProcessing]
  EP --> CTX[Context enrichment<br/>profile + credit bureau + cache]
  CTX -->|customer-context| BUS
  EP --> DE[DecisionEngine<br/>deterministic rules]
  DE --> AG[RecommendationAgent<br/>AI or scripted or rules fallback]
  AG -->|recommendations| BUS
  BUS --> UI
  UI -->|approve with consent| WF[RecommendationWorkflow<br/>ConsentService]
  WF -->|financial-actions| BUS
  BUS --> EX[ActionExecution<br/>requires valid consent]
  EX --> DB[(PostgreSQL)]
  EP & WF & EX -->|audit-events| AUD[AuditService]
```

Kafka topics: `liquidity-events`, `customer-context`, `recommendations`, `financial-actions`, `audit-events`.
Profiles: default (Postgres+Kafka), `demo` (H2+memory bus+scripted AI), `azure`/`gemini`/`openai`/`ollama` (LLM).