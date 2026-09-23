# my-drive

Almacenamiento y sincronización multimedia en la red local. El servidor vive on-premise (NUC, NAS con contenedores o Raspberry Pi); los clientes descubren la API por mDNS y sincronizan según reglas por dispositivo.

## Qué incluye este corte vertical

- API REST en Rust (Axum) con PostgreSQL y MinIO
- Portal web Angular + PrimeNG (subida, álbumes, reproductor, perfiles de sync)
- App Android nativa (descubrimiento mDNS, manifiesto JSON, descarga en paralelo, biblioteca local, actualización in-app)
- Docker Compose para levantar el stack y Watchtower para actualizar imágenes
- Clean Architecture en API, portal y Android: las capas solo se hablan por interfaces
- Catálogo de APKs en el backoffice (`/app-updates`) para actualizar móviles en la LAN

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
| musicdl-export | http://127.0.0.1:8090 (solo localhost; el portal habla con la API) |
| Salud API | http://localhost:8080/health |
| OpenAPI | http://localhost:8080/api-docs |
| MinIO consola | http://localhost:9001 |

El primer acceso abre la pantalla de **setup**: crea el usuario administrador. Después se puede iniciar sesión, subir archivos y definir perfiles de sincronización.

### mDNS

El API anuncia `_mydrive._tcp.local`. En Linux, el multicast no atraviesa la red bridge de Docker: el compose de producción usa `network_mode: host` para el API. En macOS/Windows de desarrollo, usa la IP del host o el nombre `host.docker.internal`.

La app Android reescribe URLs de manifiesto que apuntan a `localhost` para usar el host LAN descubierto. Si mDNS falla, se puede introducir `IP:puerto` a mano (por ejemplo `192.168.1.10:8080`).

### Actualizaciones on-premise

Las imágenes se publican en GHCR. En el servidor, Watchtower hace pull periódico y reinicia `api` y `web` sin tocar los volúmenes de PostgreSQL y MinIO.

```bash
docker compose -f deploy/docker-compose.prod.yml up -d
```

Las **versiones de Android** no las actualiza Watchtower. Gradle calcula `versionName` desde el último tag semver de la rama (`1.0.0` o `v1.0.0`): el commit del tag es esa versión y cada commit posterior sube el parche (`1.0.1`, `1.0.2`, …). Sin tags, empieza en `0.0.N` según el número de commits. La versión oficial (sin sufijo) solo sale al mergear a `main`; un APK de PR es `1.0.1-PR42`. `versionCode` deriva de esa semver para que las actualizaciones in-app sigan subiendo. Se publica el APK en el portal (**App updates**): el servidor lee esos campos del APK. Cada teléfono comprueba `GET /v1/app/releases/latest` tras conectar. Si el `versionCode` es mayor, descarga e instala. CI genera `my-drive-<versionName>.apk` (por ejemplo `my-drive-0.0.16-PR9.apk`) en cada push a `main`, PR y tags `v*`.

## Funcionalidades principales

1. Setup inicial e inicio de sesión (JWT)
2. Subida de fotos, vídeos y música a MinIO (prefijos `images/`, `videos/`, `music/`, `other/`; miniaturas en `images/thumbs/`) con metadatos en PostgreSQL
3. Búsqueda y descarga de canciones desde **Music** con la imagen `musicdl-export` (clientes por defecto de musicdl: Migu, NetEase, QQ, Kuwo y Qianqian). El audio queda en la biblioteca.
4. Álbumes y reproducción en el navegador (HTTP Range para vídeo/audio)
5. Perfiles de sync por dispositivo (ej. fotos del último año, vídeos &lt; 10 MB, música completa)
6. Manifiesto JSON de archivos faltantes y descarga paralela en Android
7. Android: login único; después el teléfono encuentra el servidor escaneando la LAN (mDNS `_mydrive._tcp`)
8. Navegación offline de la biblioteca descargada (álbumes, visor anterior/siguiente)
9. Publicación de APKs en el portal y actualización de Android al reconectar

## Desarrollo sin Docker (API)

Necesitas PostgreSQL y MinIO en marcha (el compose de `deploy/` sirve). Luego:

```bash
export $(grep -v '^#' .env | xargs)
cd apps/api && cargo test && cargo run -p mydrive-api
cd apps/web && npm install && npm start
cd apps/android && ./gradlew :domain:test :app:assembleRelease
```

## Estructura

```
apps/api        Rust, Clean Architecture (domain / application / infrastructure / presentation)
apps/web        Angular + PrimeNG
apps/android    Kotlin (domain JVM + app Android)
deploy          Compose, Nginx, Watchtower
```

Contexto para agentes: [AGENTS.md](AGENTS.md), [CLAUDE.md](CLAUDE.md) y `.cursor/rules/`.
