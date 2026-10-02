# Project Catalyst â€” The Autonomous Bank Account

> Every meaningful inflow is a decision moment.

Event-driven decisioning prototype for a fictional **Catalyst Bank** (customer: Thabo Mokoena). Two-Pot is the opening story; **liquidity events** (salary, bonus, tax refund, insurance payoutâ€¦) are the product.

`Detect â†’ Understand â†’ Decide â†’ Recommend â†’ Consent â†’ Act â†’ Audit`

**Rules determine eligibility. AI explains and ranks. The customer decides.** Nothing moves without explicit consent.

## Run it

### A. Fully offline (no Docker, no keys) â€” recommended for the stage
```bash
cd backend
SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run      # PowerShell: $env:SPRING_PROFILES_ACTIVE="demo"
# second terminal
cd frontend && npm install && npm run dev            # http://localhost:5173
```
Demo profile = embedded H2 + in-process event bus + deterministic recommendation engine. Backend takes ~40 s to start on a laptop.

### B. Docker Compose (PostgreSQL + Kafka)
```bash
cp .env.example .env
docker compose up --build        # UI http://localhost:3000, API http://localhost:8080
```
Uses the deterministic AI mode by default (no keys). Optional LLMs:
* OpenAI: set `OPENAI_API_KEY` in `.env`, `CATALYST_PROFILES=openai` (locally: `SPRING_PROFILES_ACTIVE=demo,openai`)
* Google Gemini: set `GEMINI_API_KEY` in `.env`, `CATALYST_PROFILES=gemini` (locally: `SPRING_PROFILES_ACTIVE=demo,gemini`)
* Azure OpenAI: set `AZURE_OPENAI_*` in `.env`, `CATALYST_PROFILES=azure`
* Ollama: `docker compose --profile ollama up`, `CATALYST_PROFILES=ollama` (pull a model first)

Any LLM failure or invalid/unsupported output falls back to the rules-only recommendation.

### Useful URLs
Swagger `/swagger-ui.html` Â· health `/actuator/health` Â· metrics `/actuator/metrics/liquidity.events.received`

## Tests
```bash
cd backend && mvn test      # 49 tests: decision engine, signals, consent, modify/decline/block, AI guardrails, full demo flow
cd frontend && npm run build
```

## Verification status (be honest on stage)
| Item | Status |
|---|---|
| Backend `mvn test` (49 tests) | Verified |
| Demo profile end-to-end via HTTP (Two-Pot, Salary, consent, audit) | Verified |
| Frontend `tsc` + `vite build` | Verified |
| `docker compose up`, PostgreSQL, Kafka paths | **Written but NOT run** (Docker was unavailable where this was built) |
| Testcontainers PG/Kafka integration tests | **Not included** (could not be run) |
| Azure OpenAI / Ollama providers | Wired, not exercised against a live model |

## Deviations from the brief
* Single Maven module; "modules" are packages (`decision`, `recommendation`, `customer`, `consent`, `audit`, `event`, â€¦).
* MapStruct not used (manual mapping); Lombok on entities only.
* "Demo Processing Timeline" latencies are measured from the demo run, not production claims.

## Docs
See [docs/developer-guide.md](docs/developer-guide.md) for step-by-step run instructions, and [docs/](docs): architecture, event-flow, decision-engine, ai-governance, demo-script, failure-scenarios.
## Refinement: the decision-support story

**Two-Pot is the use case. Liquidity events are the platform.** The default **Demo Story** tab walks six screens: Financial context -> Liquidity event -> Catalyst analysis -> Recommendation -> Customer decision (Accept / Modify / Decline) -> Action + audit. A "Pause for audience" option stops at the R45,000 arrival and at the recommendation. A full run takes about 60-90 seconds.

- Signals are **illustrative, rule-based demo signals**, not a predictive model (`docs/vulnerability-signals.md`). Cash-flow trend signals are not implemented because the demo has no transaction history.
- Event types: SALARY, BONUS, TWO_POT_PAYOUT (alias `TWO_POT_WITHDRAWAL` accepted), TAX_REFUND, INSURANCE_PAYOUT, LARGE_TRANSFER.
- Agent roles map onto existing components: Context (ContextEnrichmentService), Vulnerability (FinancialSignalService), Alternative Discovery (DecisionEngine rules), Coach (RecommendationAgent).
- New endpoints: `POST /api/recommendations/{id}/consent` (optional `amounts` to modify; may only lower amounts, never above rule limits), `GET /api/customers/{id}/financial-context`, `GET /api/pipeline/{eventId}`. Existing endpoints are unchanged.
- New audit actions: SIGNALS_DETECTED, CUSTOMER_MODIFIED, POLICY_BLOCKED, ACTION_BLOCKED, NO_ACTION_EXECUTED.
- Failure toggles on the Presenter tab: bureau down, AI down, duplicate event, **compliance block** (action fails, no money moves). Decline and a modify-over-limit (422 POLICY_BLOCKED) are shown on the Story screen; missing consent returns 403.