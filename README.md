# my-drive

Un NAS doméstico: un servidor Linux en la LAN guarda fotos, vídeos, música y archivos, y cada móvil que se conecta a la Wi-Fi encuentra el servidor por mDNS y se descarga lo que tiene para él. Hay una sola instalación (el NAS), construida desde este repo; no hay entornos de producción ni de pruebas.

## Qué incluye

- API REST en Rust (Axum) con PostgreSQL y MinIO
- Portal web Angular + PrimeNG (subida, álbumes, reproductor, perfiles de sync)
- App Android nativa (descubrimiento mDNS, manifiesto JSON, descarga, biblioteca local offline, actualización in-app)
- Un `docker-compose.yml` para el NAS
- Clean Architecture en API, portal y Android: las capas solo se hablan por interfaces
- Catálogo de APKs en el portal (`/app-updates`) para actualizar móviles en la LAN

No incluye (aún): iOS, tiendas de apps ni transcodificación de vídeo.

## Requisitos

- NAS: Linux con Docker y Docker Compose
- Desarrollo: Rust 1.85+, Node.js 22+, JDK 17+ y Android SDK para la app móvil

## Instalar y actualizar el NAS

Desde la raíz del repo:

```bash
git clone --recurse-submodules <repo> && cd my-drive   # primera vez
cp .env.example .env                                    # primera vez: cambia contraseñas y JWT_SECRET
git pull && git submodule update --init
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

`--env-file .env` hace que Compose lea el `.env` de la raíz; sin él usaría las contraseñas por defecto. Actualizar es repetir las dos últimas líneas: los volúmenes de PostgreSQL y MinIO se conservan.

| Servicio | Dónde |
| --- | --- |
| Portal web | `http://<nas>:8088` (LAN) |
| API | `http://<nas>:8080/v1` (LAN) |
| Salud / OpenAPI | `http://<nas>:8080/health` · `http://<nas>:8080/api-docs` |
| PostgreSQL, MinIO (`:9000`, consola `:9001`), musicdl (`:8090`) | solo `127.0.0.1` del NAS (usa un túnel SSH para la consola de MinIO) |

El primer acceso al portal abre la pantalla de **setup**: crea el primer usuario. Después se puede iniciar sesión, subir archivos y definir perfiles de sincronización.

### mDNS

El API anuncia `_mydrive._tcp.local`. Si `API_PUBLIC_URL` es un hostname real (no loopback), el TXT `url` lleva esa base y Android la prefiere al IP resuelto. Las URLs de loopback no se anuncian. El multicast no atraviesa la red bridge de Docker, por eso el API corre con `network_mode: host` y llega a Postgres, MinIO y musicdl por loopback.

La app Android reescribe URLs de manifiesto que apuntan a `localhost` para usar el origen descubierto. Si mDNS falla, se puede introducir el host a mano (por ejemplo `192.168.1.10:8080`).

La sincronización en segundo plano usa WorkManager: al entrar en la Wi-Fi y, mientras sigues en ella, una vez por hora.

### Versiones de Android

Los APK no van en el compose. Gradle calcula `versionName` desde el último tag semver de la rama (`1.0.0` o `v1.0.0`): el commit del tag es esa versión y cada commit posterior sube el parche (`1.0.1`, `1.0.2`, …). Sin tags, empieza en `0.0.N` según el número de commits. La versión oficial (sin sufijo) solo sale al mergear a `main`; un APK de PR es `1.0.1-PR42`. `versionCode` deriva de esa semver para que las actualizaciones in-app sigan subiendo. Se publica el APK en el portal (**App updates**): el servidor lee esos campos del APK. Cada teléfono comprueba `GET /v1/app/releases/latest` tras conectar. Si el `versionCode` es mayor, descarga e instala. CI genera `my-drive-<versionName>.apk` (por ejemplo `my-drive-0.0.16-PR9.apk`) en cada push a `main`, PR y tags `v*`. El artefacto de Actions se llama `my-drive-<versionName>` (sin `.apk`): GitHub empaqueta la descarga en zip y el APK va dentro.

## Funcionalidades principales

1. Setup inicial e inicio de sesión (JWT)
2. Subida de fotos, vídeos y música a MinIO (prefijos `images/`, `videos/`, `music/`, `other/`; miniaturas en `images/thumbs/`) con metadatos en PostgreSQL
3. Búsqueda y descarga de canciones desde **Music** con la imagen `musicdl-export` (clientes por defecto de musicdl: Migu, NetEase, QQ, Kuwo y Qianqian). El audio queda en la biblioteca.
4. Álbumes y reproducción en el navegador (HTTP Range para vídeo/audio)
5. Por defecto cada dispositivo se descarga toda la biblioteca; el perfil de sync de un dispositivo puede limitarlo (ej. fotos del último año, vídeos &lt; 10 MB)
6. Manifiesto JSON de archivos faltantes y descarga en Android
7. Android: login único; después el teléfono encuentra el servidor por mDNS (`_mydrive._tcp`, TXT `url` con el dominio) y sigue sincronizando en segundo plano. Un borrado en el portal desaparece del teléfono en el siguiente manifiesto (`removed`).
8. Navegación offline de la biblioteca descargada (álbumes, visor anterior/siguiente)
9. Publicación de APKs en el portal y actualización de Android al reconectar

## Desarrollo (en el PC)

El API arranca sin Postgres ni MinIO con el backend en memoria; los tests también lo usan:

```bash
cd apps/api && cargo test --workspace
cd apps/api && MEMORY_BACKEND=true MDNS_ENABLE=false cargo run -p mydrive-api
cd apps/web && npm install && npm start          # proxy /v1 → 127.0.0.1:8080
cd apps/android && ./gradlew :domain:test :app:assembleRelease
```

Para probar contra Postgres y MinIO reales, exporta el `.env` (`export $(grep -v '^#' .env | xargs)`) y apunta `DATABASE_URL`/`MINIO_ENDPOINT` a un servidor que los tenga.

## Estructura

```
apps/api        Rust, Clean Architecture (domain / application / infrastructure / presentation)
apps/web        Angular + PrimeNG
apps/android    Kotlin (domain JVM + app Android)
deploy          docker-compose.yml del NAS + Nginx del portal
```

Contexto para agentes: [AGENTS.md](AGENTS.md), [CLAUDE.md](CLAUDE.md) y `.cursor/rules/`.
