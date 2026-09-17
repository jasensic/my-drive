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

The portal **App updates** page publishes an APK to `POST /v1/app/releases`. After a successful connect, the app calls `GET /v1/app/releases/latest` and, if `versionCode` is higher than `BuildConfig.VERSION_CODE`, offers to download and install it (requires “install unknown apps” for my-drive). Failures surface in the app and in logcat tag `MyDriveUpdate`. On Windows:

```powershell
.\apps\android\scripts\dump-update-logs.ps1
```

Publish APKs produced by CI:

```bash
cd apps/android && ./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

GitHub Actions also assembles the APK on every PR (`android-apk` job) and on version tags (`Android APK` workflow).

## Build

Requires JDK 17 and Android SDK (compileSdk 35).

```bash
cd apps/android
./gradlew :domain:test :app:assembleRelease
```
