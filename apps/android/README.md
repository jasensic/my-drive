# Android (`apps/android`)

Native client for LAN sync. Layers:

- `:domain` — JVM Kotlin. Entities, ports, and use cases. `./gradlew :domain:test`
- `:app` / `data` — Retrofit, Room, NSD (mDNS), WorkManager, PackageInstaller
- `:app` / `presentation` — Compose UI. ViewModels call use cases only; Hilt binds ports in `AppModule`

## Connect and download

1. The phone must be on the same Wi-Fi as the server.
2. Sign in **once**. The JWT and device id stay in DataStore.
3. Later launches scan `_mydrive._tcp` (production compose uses `network_mode: host` so multicast works) and reuse the stored session. No password prompt unless the token is rejected (401).
4. `POST /v1/sync/manifest` returns files that match the device **sync profile** (portal → Devices). Those files are stored in app-private storage.
5. The library UI filters by album and opens a viewer with previous/next.

If mDNS does not resolve (typical of Docker *bridge* networking on a laptop), type `host:port` on the connect screen, for example `192.168.1.10:8080`.

URLs in the manifest often use `API_PUBLIC_URL` (`http://localhost` in compose). The client rewrites `localhost` / `127.0.0.1` to the discovered LAN host before downloading.

## In-app updates

In-app updates only work when the new APK is signed with the **same key** as the installed app. CI’s default debug keystore changes on every runner, so a portal APK cannot update an Android Studio install (log: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

One-time recovery: uninstall my-drive, then install the APK you want to keep using.

Stable signing (do not commit the `.jks`):

```bash
cd apps/android
./scripts/create-upload-keystore.sh
./gradlew :app:assembleRelease
```

Add GitHub secrets `MYDRIVE_KEYSTORE_BASE64` (base64 of the `.jks`), `MYDRIVE_STORE_PASSWORD`, `MYDRIVE_KEY_PASSWORD`, and `MYDRIVE_KEY_ALIAS=mydrive`. Local builds must use the same `keystore.properties`.

The portal **App updates** page publishes that APK. After connect, the phone installs it if `versionCode` is higher (needs “install unknown apps”). Failures show in the app and logcat tag `MyDriveUpdate`.

```powershell
.\apps\android\scripts\dump-update-logs.ps1
```

## Build

Requires JDK 17 and Android SDK (compileSdk 35).

```bash
cd apps/android
./gradlew :domain:test :app:assembleRelease
```
