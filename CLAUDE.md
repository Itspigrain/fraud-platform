# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Real-time fraud detection platform. Events are ingested, evaluated against configurable rules, and fraud alerts are generated. Dual storage: MySQL for rules/config (JPA), Elasticsearch for events/alerts (search/analytics). Redis for caching. ELK stack for log pipeline.

## Build & Run Commands

### Backend (Spring Boot / Java 21 / Maven)
```bash
mvn spring-boot:run              # run locally (needs MySQL, ES, Redis running)
mvn package -DskipTests          # build JAR
mvn test                         # run all tests
mvn test -Dtest=RuleServiceLlmTest       # run single test class
mvn test -Dtest="RuleServiceLlmTest#methodName"  # run single test method
```

### Frontend (React 19 / Vite / TypeScript)
```bash
cd frontend
npm run dev      # dev server on port 3000 (Vite)
npm run build    # typecheck + production build
npm run lint     # ESLint
```

### Docker (full stack)
```bash
docker compose up --build       # starts ES, MySQL, Redis, Logstash, Kibana, backend, frontend
```

## Architecture

### Backend (`src/main/java/com/example/fraud/`)

**Spring Boot 3.5** with these core packages:

- `rule/` — Rule domain: `RuleEntity` (JPA/MySQL), `RuleService`, `RuleEvaluationService`, `RuleController`. Rules have three types: `CONDITION` (field comparisons), `VELOCITY` (time-window counting via ES), `LLM_EVALUATOR` (LLM-based assessment).
- `event/` — Event ingestion: `EventDocument` (ES record), `EventRepository` (Spring Data ES). Events are schema-validated, stored in per-tenant ES indices.
- `fraud/` — Alert domain: `FraudAlert`, `AlertDocument`, `AlertSearchService`. Alerts link a triggered rule to the event that matched.
- `schema/` — Dynamic event schemas: `EventSchemaEntity` (MySQL), `SchemaValidationService`, `SchemaIndexService`. Tenants define their own event schemas; ES index mappings are created from schema field definitions.
- `llm/` — LLM integration: `LlmClient` interface with `OpenAiLlmClient` (OpenAI-compatible) and `NoOpLlmClient` (when `LLM_PROVIDER=none`). `LlmRuleScheduler` runs periodic LLM evaluations.
- `tenant/` — Multi-tenancy: `TenantFilter` (servlet filter) reads `X-Tenant-Id` header into `TenantContext` (ThreadLocal). `__super__` is the super-tenant value for cross-tenant access.
- `pipeline/` — `LogstashEventPublisher` writes JSON event files to a log directory, picked up by Logstash → ES.
- `geo/` — `GeoIpService` for MaxMind GeoIP lookups (optional, requires `GEOIP_DB_PATH`).
- `search/` — `AggregationService` for ES aggregation queries (stats, time series).
- `api/` — `EventController` (event ingestion + processing), `SearchController` (search/stats endpoints).
- `config/` — `WebConfig` (CORS), `RedisConfig` (caching).

### Frontend (`frontend/src/`)

**React 19 + Vite + Tailwind v4 + shadcn/ui**

- `pages/` — Dashboard, Events, Rules, Schemas, Alerts, Rule Results
- `components/ui/` — shadcn/ui primitives
- `lib/api.ts` — API client; attaches `X-Tenant-Id` header on all requests
- `lib/tenant-context.tsx` — React context for tenant selection (persisted to localStorage)
- `lib/types.ts` — Shared TypeScript types

### Event Processing Pipeline

1. Event arrives at `POST /api/events` with JSON payload
2. `SchemaValidationService` validates against the tenant's registered schema
3. Event is published to the log pipeline (`LogstashEventPublisher`) → Logstash → ES
4. Active rules for the tenant/eventType are evaluated (`RuleEvaluationService`)
5. Matching rules generate `FraudAlert` documents indexed to ES

### Multi-Tenancy

- Servlet filter (`TenantFilter`) enforces `X-Tenant-Id` header on all requests except `/actuator` and `/api/tenants`
- `TenantContext` uses ThreadLocal to carry tenant ID through the request
- ES indices are per-tenant: `events_{tenantId}`
- MySQL rules are filtered by `tenantId` column
- Super-tenant (`__super__`) bypasses tenant isolation

## Key Environment Variables

| Variable | Default | Purpose |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD` | localhost:3306 / fraud / fraud | MySQL connection |
| `SPRING_ELASTICSEARCH_URIS` | http://localhost:9200 | Elasticsearch |
| `REDIS_HOST` | localhost | Redis cache |
| `LLM_PROVIDER` | none | `openai` to enable LLM rules, `none` for no-op |
| `LLM_BASE_URL` | — | OpenAI-compatible API URL (e.g., Ollama: `http://localhost:11434/v1`) |
| `LLM_MODEL` | gpt-4o-mini | Model name |
| `LLM_API_KEY` | — | API key |
| `GEOIP_DB_PATH` | — | Path to MaxMind GeoLite2-City.mmdb (optional) |
| `VITE_API_URL` | http://localhost:8080 | Frontend → backend API URL |
| `VITE_TENANT_ID` | default | Default tenant for frontend |

## Testing

Tests use Spring Boot Test with `@MockitoExtension` (unit tests). No integration test containers configured — external services (ES, MySQL, Redis) are mocked.

```bash
mvn test    # run all backend tests
```

No frontend tests exist yet.

## Infrastructure

- **Elasticsearch 8.15**: events and alerts storage, full-text search, aggregations. Index templates initialized by `elasticsearch/init-templates.sh`.
- **Logstash 8.15**: reads JSON log files from shared volume, routes to ES. Pipeline config in `logstash/pipeline/`.
- **Kibana 8.15**: dashboards on port 5601.
- **MySQL 8.0**: rules, schemas, tenant config (JPA with `ddl-auto: update`).
- **Redis 7**: caching layer (60s TTL).

## API Documentation

OpenAPI spec at `docs/openapi.yaml`.

## Docker Compose Services

| Service | Image | Port | Purpose |
|---|---|---|---|
| `elasticsearch` | elasticsearch:8.15.0 | 9200 | Event/alert storage and search |
| `init-templates` | curlimages/curl | — | Initializes ES index templates on startup |
| `logstash` | logstash:8.15.0 | — | Reads JSON log files, routes to ES |
| `kibana` | kibana:8.15.0 | 5601 | Dashboards and visualization |
| `mysql` | mysql:8.0 | 3306 | Rules, schemas, tenant config |
| `redis` | redis:7-alpine | 6379 | Caching layer |
| `fraud-app` | (built from `./Dockerfile`) | 8080 | Spring Boot backend |
| `frontend` | (built from `./frontend`) | 3000 | React frontend |

## Seed Data

```bash
scripts/seed-data.sh       # seed sample events
scripts/seed-farming.sh    # seed farming tenant data
scripts/seed-bulk.sh       # bulk event seeding
```
