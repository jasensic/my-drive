# AGENTS.md

Monorepo de **my-drive**: almacenamiento y sync multimedia en LAN.

## Mapa

| Ruta | Qué es | Cómo probar |
| --- | --- | --- |
| `apps/api` | API Rust (workspace Cargo) | `cd apps/api && cargo test && cargo run -p mydrive-api` |
| `apps/web` | Portal Angular + PrimeNG | `cd apps/web && npm test -- --watch=false` / `npm start` |
| `apps/android` | App Kotlin + módulo `:domain` JVM | `./gradlew :domain:test` |
| `deploy` | Compose, Nginx, Watchtower | `docker compose -f deploy/docker-compose.yml up --build` |

Contrato **entre procesos**: OpenAPI en `GET /api-docs/openapi.json`.
Contrato **entre capas**: traits / `interface` / injection tokens. Nunca importar implementaciones concretas hacia una capa interna.

## Clean Architecture (obligatorio)

Capas, de dentro a fuera:

1. **domain** — entidades, value objects, errores, **puertos** (interfaces). Cero frameworks.
2. **application** — casos de uso. Solo hablan con puertos del domain.
3. **infrastructure / data** — sqlx, MinIO, JWT, mDNS, HttpClient, Room. Implementan puertos.
4. **presentation** — Axum, componentes Angular/PrimeNG, Compose. Solo casos de uso. Mapean DTO ↔ entidades.

Reglas:

- Toda comunicación entre capas es por **interfaces** (traits en Rust, `interface` + `InjectionToken` en Angular, `interface` en Kotlin).
- Prohibido que presentation llame a repositorios, `HttpClient`, sqlx o MinIO.
- El wiring (inyección de implementaciones) solo en el borde: `apps/api/crates/presentation/src/main.rs`, `apps/web/src/app/app.config.ts`, módulos Hilt de Android.
- Un cambio de tecnología (Axum, Postgres, PrimeNG HTTP, Retrofit) debe tocar `infrastructure`/`data` y el composition root, no el domain.

## Extender reglas de sync

1. Ajusta el modelo en `apps/api/crates/domain` (`SyncRule`, `MediaKind`) y el evaluador `domain::sync`.
2. Tests unitarios en domain **sin I/O**.
3. Persistencia en infrastructure (migración sqlx).
4. DTO OpenAPI en presentation.
5. Replica el contrato en `apps/web/src/app/domain` y `apps/android/domain`.

## Convenciones

- API versionada bajo `/v1`.
- Servicio mDNS: `_mydrive._tcp`.
- Commits en inglés, mensajes en imperativo.
- No commitear `.env` ni keystores.
