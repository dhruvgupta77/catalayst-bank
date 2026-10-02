# Developer Guide — Running Project Catalyst

## Prerequisites
| Tool | Needed for | Version |
|---|---|---|
| JDK | local backend | 21 |
| Maven | local backend | 3.9+ |
| Node + npm | local frontend | 20+ (tested on 24) |
| Docker Desktop | Docker run | Compose v2 |

No API keys or internet are needed for the default demo.

---

## Option A — Run locally (offline demo profile)
Uses embedded H2, in-process event bus and deterministic AI. No Docker.

**1. Start the backend** (terminal 1)

PowerShell:
```powershell
cd project-catalyst\backend
$env:SPRING_PROFILES_ACTIVE="demo"
mvn spring-boot:run
```
bash:
```bash
cd project-catalyst/backend
SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run
```
Wait ~40 s for `Started CatalystApplication`.

**2. Check it**
```bash
curl http://localhost:8080/actuator/health      # {"status":"UP",...}
```
Swagger UI: http://localhost:8080/swagger-ui.html

**3. Start the frontend** (terminal 2)
```bash
cd project-catalyst/frontend
npm install
npm run dev
```
Open http://localhost:5173 (Vite proxies `/api` to `localhost:8080`; override with `VITE_BACKEND`).

**4. Smoke test from the CLI (optional)**
```bash
curl -X POST http://localhost:8080/api/demo/liquidity-events -H "Content-Type: application/json" -d '{"scenario":"TWO_POT_PAYOUT"}'
# -> eventId; then:
curl http://localhost:8080/api/liquidity-events/<eventId>        # gives recommendationId
curl http://localhost:8080/api/recommendations/<recId>            # options, rules, narrative
curl -X POST http://localhost:8080/api/recommendations/<recId>/approve -H "Content-Type: application/json" \
  -d '{"confirmed":true,"actionIds":["<ACT-1>","<ACT-2>","<ACT-3>"]}'
curl http://localhost:8080/api/audit/<eventId>                    # full timeline
```

**5. Run the packaged jar instead (faster restarts)**
```bash
mvn -DskipTests package
SPRING_PROFILES_ACTIVE=demo java -jar target/catalyst-backend-1.0.0.jar
```

**6. Tests / build**
```bash
cd backend && mvn test          # 38 tests
cd frontend && npm run build
```

**Stop:** Ctrl+C in each terminal. Demo data is in-memory; `POST /api/demo/reset` restores seed data between runs.

---

## Option B — Run with Docker Compose (PostgreSQL + Kafka)
> Written but not yet executed in the build environment — expect to fix small issues on first run.

**1. Configure**
```bash
cp .env.example .env          # PowerShell: Copy-Item .env.example .env
```
Edit `DB_PASSWORD` if desired. Leave Azure values blank for deterministic mode.

**2. Build and start**
```bash
docker compose up --build
```
Startup order: postgres and kafka (healthy) → backend (Flyway migrates + seeds) → frontend.

**3. Open**
* UI: http://localhost:3000
* API: http://localhost:8080 , health: http://localhost:8080/actuator/health

**4. Verify Kafka is used**
```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic liquidity-events --from-beginning
```
Trigger an event in the UI; messages should appear.

**5. Inspect the database**
```bash
docker compose exec postgres psql -U catalyst -d catalyst -c "select action, actor, result from audit_events order by id;"
```

**6. Logs / stop**
```bash
docker compose logs -f backend
docker compose down            # add -v to wipe volumes
```

**Optional LLM**
* OpenAI: set `OPENAI_API_KEY` (optionally `OPENAI_MODEL`, default `gpt-4o-mini`) in `.env` and `CATALYST_PROFILES=openai`. Locally: `SPRING_PROFILES_ACTIVE=demo,openai` with `OPENAI_API_KEY` exported.
* Google Gemini: set `GEMINI_API_KEY` (and optionally `GEMINI_MODEL`) in `.env` and `CATALYST_PROFILES=gemini`. Locally: `SPRING_PROFILES_ACTIVE=demo,gemini` with `GEMINI_API_KEY` exported.
* Azure OpenAI: set `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_DEPLOYMENT` and `CATALYST_PROFILES=azure` in `.env`, then `docker compose up`.
* Ollama: `docker compose --profile ollama up`, `CATALYST_PROFILES=ollama`, and pull a model: `docker compose exec ollama ollama pull llama3.1`.
* If the LLM fails or returns invalid output, the app falls back to the rules-only recommendation.

---

## Troubleshooting
| Symptom | Fix |
|---|---|
| Port 8080/5173/3000 in use | Stop the other process or change the port mapping |
| UI shows network errors | Backend not finished starting; check `/actuator/health` |
| `mvn` says `release 21 not supported` | Set `JAVA_HOME` to JDK 21 |
| Backend can't reach DB in Docker | Check `docker compose ps`; postgres must be healthy |
| Stale demo state | `curl -X POST http://localhost:8080/api/demo/reset` |