# my-drive

Almacenamiento y sincronización multimedia en la red local. El servidor vive on-premise (NUC, NAS con contenedores o Raspberry Pi); los clientes descubren la API por mDNS y sincronizan según reglas por dispositivo.

## Qué incluye este corte vertical

- API REST en Rust (Axum) con PostgreSQL y MinIO
- Portal web Angular + PrimeNG (subida, álbumes, reproductor, perfiles de sync)
- App Android nativa (descubrimiento mDNS, manifiesto JSON, descarga en paralelo)
- Docker Compose para levantar el stack y Watchtower para actualizar imágenes
- Clean Architecture en API, portal y Android: las capas solo se hablan por interfaces

No incluye (aún): iOS, Fastlane / tiendas, gRPC ni transcodificación de vídeo.

## Requisitos

- Docker y Docker Compose
- (Desarrollo) Rust 1.85+, Node.js 22+, JDK 17+ y Android SDK para la app móvil

## Puesta en marcha (servidor local)

```bash
cp .env.example .env
docker compose -f deploy/docker-compose.yml up --build
```

Servicios:

| Servicio | URL |
| --- | --- |
| Portal web | http://localhost |
| API | http://localhost/v1 (también http://localhost:8080/v1 en compose de desarrollo) |
| Salud API | http://localhost:8080/health |
| OpenAPI | http://localhost:8080/api-docs |
| MinIO consola | http://localhost:9001 |

El primer acceso abre la pantalla de **setup**: crea el usuario administrador. Después se puede iniciar sesión, subir archivos y definir perfiles de sincronización.

### mDNS

El API anuncia `_mydrive._tcp.local`. En Linux, el multicast no atraviesa la red bridge de Docker: el compose de producción usa `network_mode: host` para el API. En macOS/Windows de desarrollo, usa la IP del host o el nombre `host.docker.internal`.

### Actualizaciones on-premise

Las imágenes se publican en GHCR. En el servidor, Watchtower hace pull periódico y reinicia `api` y `web` sin tocar los volúmenes de PostgreSQL y MinIO.

```bash
docker compose -f deploy/docker-compose.prod.yml up -d
```

## Funcionalidades principales

1. Setup inicial e inicio de sesión (JWT)
2. Subida de fotos, vídeos y música a MinIO con metadatos en PostgreSQL
3. Álbumes y reproducción en el navegador (HTTP Range para vídeo/audio)
4. Perfiles de sync por dispositivo (ej. fotos del último año, vídeos &lt; 10 MB, música completa)
5. Manifiesto JSON de archivos faltantes y descarga paralela en Android
6. Descubrimiento automático del servidor en la Wi-Fi local

## Desarrollo sin Docker (API)

Necesitas PostgreSQL y MinIO en marcha (el compose de `deploy/` sirve). Luego:

```bash
export $(grep -v '^#' .env | xargs)
cd apps/api && cargo test && cargo run -p mydrive-api
cd apps/web && npm install && npm start
cd apps/android && ./gradlew :domain:test
```

## Estructura

```
apps/api        Rust, Clean Architecture (domain / application / infrastructure / presentation)
apps/web        Angular + PrimeNG
apps/android    Kotlin (domain JVM + app Android)
deploy          Compose, Nginx, Watchtower
```

Contexto para agentes: [AGENTS.md](AGENTS.md), [CLAUDE.md](CLAUDE.md) y `.cursor/rules/`.
