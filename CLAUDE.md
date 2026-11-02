# CLAUDE.md

**my-drive** es un NAS doméstico: un servidor Linux en la LAN guarda fotos, vídeos, música y archivos, y cada dispositivo que se conecta a la Wi-Fi descubre el servidor por mDNS y **descarga automáticamente lo que el servidor tiene para él**. Los dispositivos usan su copia local offline; el servidor es la fuente de verdad.

**No hay entornos.** Existe una sola instalación (el NAS), construida desde este repo, y el bucle de desarrollo en el PC. No añadas variantes “prod/staging/dev” de compose, imágenes publicadas ni auto-updaters: el objetivo es simplificar.

Lee también [AGENTS.md](AGENTS.md) y `.cursor/rules/*.mdc` antes de editar. Commits en inglés y en imperativo. No commitees `.env` ni keystores.

## El NAS (única instalación)

```bash
# Primera vez
git clone --recurse-submodules <repo> && cd my-drive
cp .env.example .env        # cambia contraseñas y JWT_SECRET
# Arrancar y actualizar (siempre desde la raíz, con --env-file)
git pull && git submodule update --init
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

- `--env-file .env` es obligatorio: sin él, Compose interpola `${…}` desde `deploy/.env` (no existe) y usa las contraseñas por defecto aunque el `.env` de la raíz diga otra cosa.
- Topología ([deploy/docker-compose.yml](deploy/docker-compose.yml)): el **API** va en `network_mode: host` (el multicast mDNS no atraviesa la red bridge) y alcanza por `127.0.0.1` a Postgres `:5432`, MinIO `:9000` (consola `:9001`) y musicdl `:8090`, que solo escuchan en loopback. A la LAN solo se exponen el **API `:8080`** y el **portal `:8088`** (Nginx: SPA + proxy `/v1`, `/api-docs`, `/health` hacia `host.docker.internal:8080`).
- Datos: volúmenes `pgdata`, `miniodata`, `musicdl-downloads` (proyecto Compose `deploy`). `docker compose down` no los borra; `down -v` sí.
- Primer acceso al portal → pantalla de **setup** (crea el primer usuario).
- APKs de Android: se publican desde el portal (**App updates**) y los teléfonos se actualizan al reconectar.

## Bucle de desarrollo (PC)

```bash
# API (Rust 1.85+, edition 2024). Los tests usan MemoryStore: no necesitan Postgres/MinIO.
cd apps/api && cargo test --workspace
MEMORY_BACKEND=true MDNS_ENABLE=false cargo run -p mydrive-api   # API sin dependencias
#   Windows: la toolchain GNU falla ("dlltool.exe not found") y la MSVC necesita
#   Visual Studio Build Tools (link.exe). Sin ellas, compila en Docker:
docker run --rm -v "$PWD/apps/api:/app" -v mydrive-cargo-target:/app/target \
  -v mydrive-cargo-registry:/usr/local/cargo/registry -w /app rust:1.94-bookworm cargo test --workspace

# Portal (Node 22, Angular 22, PrimeNG 22, Vitest, Playwright)
cd apps/web && npm install && npm start          # ng serve + proxy /v1 → 127.0.0.1:8080
cd apps/web && npm test -- --watch=false
cd apps/web && npm run test:e2e                  # requiere API + portal en marcha

# Android (JDK 17; :domain es JVM puro, sin emulador)
cd apps/android && ./gradlew buildSrc:test :domain:test
cd apps/android && ./gradlew :app:assembleRelease
```

CI (`.github/workflows/ci.yml`): `cargo test --workspace`, tests + build del portal, `:domain:test`, APK release y Playwright contra un API `MEMORY_BACKEND` + `ng serve`. `android-release.yml` adjunta el APK firmado a la GitHub Release de cada tag `v*`. No se publican imágenes Docker.

## Mapa del monorepo

| Ruta | Qué es |
| --- | --- |
| `apps/api` | Workspace Cargo: `domain`, `application`, `infrastructure`, `presentation` (binario `mydrive-api`) + `migrations/` sqlx |
| `apps/web` | Portal Angular (standalone + signals) en `src/app/{domain,application,data,presentation}`; e2e en `e2e/` con page objects |
| `apps/android` | `:domain` (Kotlin JVM, casos de uso y puertos) + `:app` (`data` = Retrofit/OkHttp/Room/NSD/WorkManager/Media3, `presentation` = Compose + ViewModels, Hilt) |
| `services/musicdl` | Submódulo git (CharlesPikachu/musicdl) |
| `services/musicdl-server/server.py` | Sidecar HTTP Python (`:8090`) sobre musicdl: búsqueda asíncrona con `search_id`, parse de playlists de Spotify, descarga |
| `deploy/` | `docker-compose.yml` del NAS y `nginx/nginx.conf` del portal |

## Clean Architecture (obligatoria en las tres apps)

- domain → entidades, value objects, errores y **puertos**. Sin frameworks ni I/O.
- application → casos de uso; solo hablan con puertos.
- infrastructure / data → implementaciones (sqlx, aws-sdk-s3/MinIO, JWT, argon2, mdns-sd, ffmpeg, HttpClient, Retrofit, Room).
- presentation → Axum / componentes PrimeNG / Compose. Solo llaman a casos de uso y mapean DTO ↔ entidad.
- Wiring solo en `apps/api/crates/presentation/src/main.rs` (+ `infrastructure::build_deps` y `application::Services::new`), `apps/web/src/app/app.config.ts` (InjectionTokens en `domain/ports.ts` y `application/use-cases.tokens.ts`) y `apps/android/app/.../data/AppModule.kt` (Hilt).
- No saltes un puerto “para ir más rápido”. Si un caso de uso necesita I/O nuevo: trait en domain + implementación en infrastructure (y en `infrastructure/src/memory.rs` y `application/src/test_support.rs` para los tests).
- Usa `sqlx::query`/`query_as` en runtime (no macros `query!`): CI compila sin Postgres.

## Backend (`apps/api`)

**Composición.** `Deps` (application/src/lib.rs) agrupa todos los puertos como `Arc<dyn …>`; `Services` crea un `Arc<dyn UseCase>` por caso de uso. Cada servicio recibe `Arc<Deps>` completo. `MEMORY_BACKEND=true` sustituye Postgres+MinIO por `MemoryStore` y ffmpeg por `FakeAudioTranscoder`.

**Modelo** (`domain/src/model.rs`): `User`, `Album` (con `silo`), `FileRecord` (objeto original + `thumbnail_key` + variante móvil `mobile_*` + `deleted_at` para papelera), `Device`, `SyncProfile { rules: Vec<SyncRule> }`, `Share` (file|album, read|write), `AppRelease`. Ids tipados con macro en `ids.rs` (UUID v4).

- `MediaKind` = photo | video | audio | other, derivado del MIME en la subida.
- `LibrarySilo` = music (audio) | photos (photo+video) | files (other). Un álbum solo acepta archivos de su silo.
- Claves de MinIO: `{images|videos|music|other}/{file_id}/{nombre saneado}`, miniaturas `images/thumbs/{id}.jpg`, AAC móvil `music/{id}/mobile.m4a`, APKs `app-releases/{id}.apk`.
- Papelera: `deleted_at`; purga automática a los 30 días (`TRASH_RETENTION_DAYS`), ejecutada al listar la papelera (`ListTrash`).

**Permisos** (`application/src/access.rs`): owner > write > read. Un archivo es accesible si es tuyo, si está en un álbum tuyo, o si te lo compartieron (directamente o vía álbum). Solo el owner renombra/borra/comparte; `write` en un álbum permite subir/mover archivos a él. Aparte, `users.is_admin` (migración `0010`): el usuario del setup es admin, y solo un admin crea usuarios y publica, edita o borra APKs (`device_tokens.rs::require_admin`).

**Auth.** `POST /v1/setup` crea el primer usuario, que es admin (solo si `users` está vacía). No hay auto-registro: el admin crea usuarios con `POST /v1/users`. JWT HS256 de 7 días (`infrastructure/src/jwt.rs`); argon2 para contraseñas. Cada dispositivo recibe al registrarse (`POST /v1/devices`) un **refresh token** (`device_token`, 64 hex; el servidor guarda solo su SHA-256 en `devices.token_hash`), que cambia por un JWT nuevo en `POST /v1/auth/refresh`. `POST /v1/devices/{id}/token` lo rota, y `DELETE /v1/devices/{id}` revoca el dispositivo. `JWT_SECRET` es obligatorio (≥ 32 caracteres y distinto del de la plantilla) salvo con `MEMORY_BACKEND`.

**Media.** Al subir: SHA-256 (`checksum = "sha256:<hex>"`), objeto a MinIO, miniatura JPEG 320px (crate `image`, solo JPEG/PNG; portadas embebidas FLAC/ID3/MP4 para audio). Audio sin pérdida (FLAC/WAV/AIFF/ALAC) obtiene un derivado AAC 256 kbps vía `ffmpeg` (`mobile.rs::ensure_mobile_audio`, serializado por archivo con `FileTranscodeLocks`). `GET /v1/files/{id}/content` admite `Range` (206) y `?variant=mobile`.

**mDNS** (`infrastructure/src/mdns.rs`): anuncia `_mydrive._tcp.local.` con TXT `path=/v1`, `api=/v1` y `url=` (= `API_PUBLIC_URL` si no es loopback, o `http://<IP LAN>:<puerto>`).

**Música.** `/v1/music/search` → `search_id`; el portal hace polling de `/v1/music/search/{id}`; `/v1/music/import` descarga del sidecar y reutiliza `UploadFile`.

**Endpoints** (todo bajo `/v1` salvo `/health` y `/api-docs`; OpenAPI en `GET /api-docs/openapi.json`, definido en `presentation/src/openapi.rs` — registra ahí cada handler nuevo):
`status`, `setup`, `login`, `auth/refresh`, `me`, `users` (GET; POST solo admin) · `albums[/{id}]` · `files[?silo=]`, `files/{id}` (GET/PATCH/DELETE=purga), `files/{id}/{trash,restore,content,thumbnail}`, `files/trash` (GET/DELETE) · `devices`, `devices/{id}` (DELETE), `devices/{id}/token` (POST), `devices/{id}/sync-profile` (GET/PUT), `devices/{id}/exclusions` (PUT, merge) · `shares[/{id}]` · `sync/manifest` (POST) · `app/releases` (GET; POST solo admin), `app/releases/{inspect,latest}`, `app/releases/{id}` (PATCH/DELETE solo admin), `app/releases/{id}/apk` · `music/{search,search/{id},search/{id}/cancel,playlist,import}`.

**Migraciones**: `apps/api/migrations/000N_*.sql`, se aplican al arrancar (`PgRepos::migrate`). Añade siempre una nueva; nunca edites una ya aplicada en el NAS.

## Protocolo de sincronización (el corazón del producto)

1. **Descubrimiento** (Android `NsdServerDiscovery`): NSD `_mydrive._tcp` con multicast lock, 15 s de timeout; si falla, último servidor guardado o host manual (`192.168.1.10:8080`). `lanUrl()` reescribe URLs `localhost` al origen descubierto.
2. **Sesión** (`SignInUseCase`, en primer plano desde el ViewModel): login o setup una vez. Si el teléfono ya tenía un dispositivo para ese usuario, pide un token nuevo (`devices/{id}/token`); si no, o si se olvidó en el portal, se registra otra vez. Se guardan en DataStore el JWT, el `deviceId` y el `deviceToken`, nunca la contraseña. Cada sync empieza renovando el JWT con `auth/refresh`; las sesiones anteriores a los refresh tokens consiguen uno con su JWT todavía válido.
3. **Manifiesto**: `POST /v1/sync/manifest { device_id, last_sync_at, have_file_ids }` → `{ generated_at, files[], albums[], removed[] }`.
   - `files`: todos los archivos accesibles, no en papelera, no excluidos en ese dispositivo, que cumplen alguna `SyncRule` del perfil (`domain/src/sync.rs::evaluate_manifest`). Incluye los que el dispositivo ya tiene (para propagar renombres/álbumes).
   - Audio con variante móvil → `url` con `?variant=mobile` y tamaño/MIME/checksum del AAC.
   - `removed`: ids de `have_file_ids` que están en papelera, borrados, excluidos o ya no accesibles. **Lo que solo deja de cumplir las reglas se queda en el dispositivo.**
   - `last_sync_at` se envía pero el servidor lo ignora: el manifiesto es siempre completo.
4. **Descarga** (`domain/.../SyncFilesUseCase.kt`): borra `removed`, reemplaza álbumes, descarga cada archivo que falta (hoy secuencialmente), verifica SHA-256, hace `commit` en Room y trae carátulas de audio. Al final guarda `generated_at` como `lastSyncAt`.
5. **Disparadores** (`WorkManagerSyncScheduler`, `WifiSyncReceiver`, `SyncWorker` en foreground `dataSync`): al entrar en una Wi-Fi nueva, cada hora mientras hay red, y manualmente. WorkManager solo recibe el host manual, nunca credenciales. Un 401 (también un refresh revocado) se traduce en `"login required"` y cierra la sesión local.
6. **Exclusiones**: “quitar de este dispositivo” borra la copia local y encola el id; se envía con `PUT /v1/devices/{id}/exclusions` en el siguiente sync (cola offline en DataStore).
7. **Actualización in-app**: tras sincronizar, `GET /v1/app/releases/latest`; si `versionCode` > `BuildConfig.VERSION_CODE`, descarga, verifica checksum e instala (`REQUEST_INSTALL_PACKAGES`).

Perfil por defecto (`application/src/usecases/devices.rs::default_profile`): **NAS completo**, `include_all` en los cuatro tipos, así que un dispositivo nuevo se descarga todo lo accesible. La migración `0009` pasó a este perfil los dispositivos que seguían en el antiguo (fotos del último año, vídeos < 10 MB). Las reglas por tipo siguen existiendo para limitar un dispositivo concreto desde el portal (**Devices**).

Si cambias reglas de sync, sigue AGENTS.md → “Extender reglas de sync” y replica el contrato en `apps/web/src/app/domain` y `apps/android/domain`.

## Android

- Almacenamiento local privado: `filesDir/users/{userId}/media/{fileId}` (legacy `filesDir/media`), carátulas y caché aparte; Room por usuario (`AppDb` v4, `fallbackToDestructiveMigration`). Sesión, servidor y exclusiones en DataStore.
- `LocalMediaStore.library()` debe ser rápido (sin decodificar tags); `enrichMetadata()` rellena tags/carátulas después.
- Navegación remota sin descargar: `BrowseRemoteLibraryUseCase` + `StreamRemoteFileUseCase` (Coil/ExoPlayer con `Authorization` inyectado por interceptor).
- Reproducción: Media3 `PlaybackService` (MediaSession, Android Auto vía `automotive_app_desc.xml`), cola en `domain/PlaybackQueue.kt`.
- Versionado desde git (`buildSrc/AndroidGitVersion.kt`): último tag semver + 1 parche por commit, sufijo `-PR<n>` en PRs. No hardcodees `versionName`/`versionCode`.
- `usesCleartextTraffic="true"`: el tráfico LAN va en HTTP plano.

## Portal

- Rutas (`app.routes.ts`): `/login` (setup/login; sin registro), y bajo `ShellComponent` con `authGuard`: `/photos`, `/music`, `/files` (mismo `LibraryPage` con `data.silo`), `/imports` (Google Photos Picker, Google Drive Picker, Spotify PKCE → solo metadatos), `/player/:id`, `/devices` (perfiles de sync, olvidar dispositivo), `/users` y `/app-updates` (solo se muestran al admin; el flag `is_admin` se guarda con la sesión y se refresca con `/v1/me` al cargar el shell).
- JWT en `localStorage` (`LocalTokenStore`) + `authInterceptor`. Medios autenticados se cargan como `Blob` (`LOAD_MEDIA_BLOB`) porque `<img>/<video>` no envían el header.
- Claves de Google/Spotify y licencia PrimeNG se inyectan en build con `scripts/ng-env.mjs` desde el `.env` de la raíz (el Dockerfile lo copia si existe).
- PrimeNG solo en `presentation/`. Componentes inyectan casos de uso por token, nunca `HttpClient` ni clases de `data/`.

## Configuración

- Un único `.env` en la raíz (plantilla `.env.example`) sirve al compose del NAS y a `cargo run`/`npm start`: Postgres, MinIO (`MINIO_*`), `JWT_SECRET`, `API_PUBLIC_URL`, `API_HTTP_PORT`, `PORTAL_HTTP_PORT`, `MDNS_*`, `MUSICDL_*`, Google/Spotify, `PRIMENG_LICENSE`. El compose sobrescribe en el API `DATABASE_URL`, `MINIO_ENDPOINT` y `MUSICDL_URL` a loopback.
- `Settings::from_env` tiene valores por defecto para casi todo, pero **no arranca sin un `JWT_SECRET` real** (salvo `MEMORY_BACKEND=true`, que usa uno fijo de desarrollo). Genera uno con `openssl rand -hex 32`.
- Límite de cuerpo 512 MB en Axum y en Nginx (`client_max_body_size`). Nginx sin buffering y timeouts de 300 s en `/v1`.

## Deuda conocida y trampas (revísalo antes de tocar sync, media o auth)

- **Todo en memoria**: `ObjectStore::put/get/get_range` usan `Bytes` completos; subida, descarga sin `Range`, `Range` abierto (`bytes=N-`), transcodificación y miniaturas cargan el archivo entero en RAM (y `s3.rs` lo copia otra vez con `to_vec`). Vídeos grandes → picos de memoria; > 512 MB no se pueden subir.
- **Manifiesto caro**: `BuildSyncManifest` llama a `ensure_mobile_audio` para cada audio de forma secuencial dentro de la petición; el primer sync con muchos FLAC transcodifica toda la biblioteca en ese request. También es O(biblioteca) en cada sync (sin delta ni cursor).
- **CPU en el runtime async**: miniaturas (`image`) se generan dentro de handlers async sin `spawn_blocking`.
- **Auth**: el portal no tiene refresh token (se vuelve a iniciar sesión cada 7 días); CORS `Any`; no hay forma de quitar ni degradar usuarios ni de cambiar contraseñas.
- **Android sync**: descargas secuenciales y sin reanudación (escribe directo al destino, sin `.part` ni `Range`); un error aborta todo el sync; `reuseLocalFile` recalcula SHA-256 de cada archivo ya descargado en cada sync; se reintenta la carátula de cada audio sin portada en cada sync.
- **NAS**: no hay copias de seguridad de `pgdata`/`miniodata`; HTTP plano en la LAN.
- GC de papelera solo al llamar `GET /v1/files/trash` (efecto lateral en un GET).

## Hoja de ruta (cambios planificados)

Orden sugerido; cada punto es un PR propio. Al terminar uno, muévelo fuera de esta lista y actualiza las secciones de arriba.

1. **Sync de Android robusto**: 3–4 descargas en paralelo, `.part` + `Range` para reanudar, fallos aislados por archivo, checksum verificado guardado en Room (sin re-hash), comprobación de espacio libre y no reintentar carátulas inexistentes.
2. **Streaming en el servidor**: `ObjectStore` con `put_stream`/`get_stream`, hash calculado mientras se escribe, subidas troceadas y reanudables (> 512 MB), y miniaturas con `spawn_blocking`. Valorar un adaptador de sistema de archivos como alternativa a MinIO.
3. **Trabajo en segundo plano**: tabla `jobs` (`FOR UPDATE SKIP LOCKED`) para AAC, miniaturas (incl. HEIC/WebP y fotograma de vídeo), EXIF y purga de papelera. El manifiesto nunca transcodifica.
4. **Sync por cambios**: tabla `changes` con número de secuencia y `GET /v1/sync/changes?cursor=N` paginado en lugar del manifiesto completo.
5. **URLs firmadas de corta duración** para `<img>/<video>`, ExoPlayer y Coil, así el portal reproduce en streaming sin descargar Blobs.
6. **Copias de seguridad** de `pgdata`/`miniodata` (`pg_dump` + `mc mirror`) y un `/health` que compruebe la base de datos y MinIO.
7. **Rediseño de la interfaz web y Android: estética tipo Apple, minimalista y fácil de usar.**
   - **Un solo sistema de diseño para las dos apps.** Hoy no coinciden: el portal usa salvia/rosa/arena (`apps/web/src/app/presentation/theme.preset.ts`, tokens `--md-*` en `apps/web/src/styles.scss`) y Android verde azulado/ámbar (`presentation/theme/Color.kt`, `Theme.kt`). Define los tokens una vez (color, tipografía, espaciado, radios, sombras) en una tabla de este fichero y replícalos en ambos sitios.
   - **Color**: superficies neutras (blanco/gris muy claro; negro/gris grafito en oscuro), un único color de acento y rojo solo para acciones destructivas. Claro/oscuro automático según el sistema (el portal ya tiene `ThemeModeService`).
   - **Tipografía y espacio**: fuente del sistema (`-apple-system, BlinkMacSystemFont, "SF Pro Text"`, Inter de respaldo; en Android la del sistema), títulos grandes, mucho aire, jerarquía por tamaño y peso en vez de bordes y cajas. Radios de 10–14 px y sombras muy suaves.
   - **Contenido primero**: fotos en una cuadrícula a sangre con visor a pantalla completa y gestos; música como lista limpia con carátula y un mini-reproductor fijo; archivos como lista sencilla. Barras de navegación translúcidas (`backdrop-filter: blur` en web).
   - **Fácil de usar**: en el móvil, tres pestañas (Fotos, Música, Archivos) y los ajustes en una hoja; primer arranque en una sola pantalla (encontrar el servidor → iniciar sesión → sincronizar); el estado del sync como un indicador discreto, no como un panel; menos opciones visibles y las avanzadas (reglas de sync, publicar APK) agrupadas en Ajustes.
   - **Plataforma**: en Android se mantiene Material 3 como base técnica, retocado con estos tokens (títulos grandes, listas agrupadas con margen); no copies componentes de iOS que rompan la navegación de Android (gesto atrás, botón atrás).
   - **Restricciones**: PrimeNG sigue solo en `presentation/`; conserva roles y nombres accesibles para que los page objects de Playwright (`apps/web/e2e/pages/`) sigan funcionando, o actualízalos en el mismo PR. Haz web y Android en PRs separados.
