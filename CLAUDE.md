# CLAUDE.md

Este repositorio es **my-drive**, un servidor de archivos y sincronización en red local (on-premise).

Lee también [AGENTS.md](AGENTS.md) y `.cursor/rules/` antes de editar.

## Comandos

```bash
# API
cd apps/api && cargo test && cargo run -p mydrive-api

# Portal
cd apps/web && npm install && npm start
cd apps/web && npm test -- --watch=false
cd apps/web && npm run test:e2e

# Android (lógica de dominio, sin emulador; APK de release)
cd apps/android && ./gradlew :domain:test :app:assembleRelease

# Stack
docker compose -f deploy/docker-compose.yml up --build
```

## Clean Architecture

Backend (`apps/api/crates/{domain,application,infrastructure,presentation}`), portal (`apps/web/src/app/{domain,application,data,presentation}`) y Android (`domain` / `data` / `presentation`) usan la misma regla:

- Las capas internas no conocen las externas.
- Comunicación **solo por interfaces**.
- PrimeNG, Axum, sqlx, Retrofit y Room son detalles de presentation o infrastructure.
- Composición únicamente en `main.rs`, `app.config.ts` y Hilt.

No saltes un puerto para “ir más rápido”. Si el caso de uso necesita I/O, añade un trait en domain y una implementación en infrastructure/data.

## Contrato HTTP

REST `/v1`, JWT tras `POST /v1/setup` o `POST /v1/login`. El manifiesto de sync es `POST /v1/sync/manifest`. Catálogo de APKs en `/v1/app/releases`. OpenAPI en `/api-docs`.
