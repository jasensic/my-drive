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

Add **repository** secrets (Settings → Secrets and variables → Actions → *Repository* secrets — not an Environment):

- `MYDRIVE_KEYSTORE_BASE64` (base64 of the `.jks`, no PEM headers)
- `MYDRIVE_STORE_PASSWORD`
- `MYDRIVE_KEY_PASSWORD` (same value unless the key uses another)
- `MYDRIVE_KEY_ALIAS=mydrive`

CI (`ci.yml` / `android-release.yml`) reads `${{ secrets.* }}` with **no** `jobs.*.environment`. Secrets attached to a GitHub Environment (`production`, `staging`, …) never reach the job, so Gradle generates a new debug certificate and in-app updates fail with a signing/certificate error.

If you really want Environment secrets, add `environment: <name>` to the `android-apk` / `apk` jobs and put the four secrets in that Environment. Local builds must use the same `keystore.properties`.

The portal **App updates** page publishes that APK. After connect, the phone installs it if `versionCode` is higher (needs “install unknown apps”). Failures show in the app and logcat tag `MyDriveUpdate`.

```powershell
.\apps\android\scripts\dump-update-logs.ps1
```

## Versioning

`versionName` comes from the highest semver git tag reachable from `HEAD` (`1.0.0` or `v1.0.0`):

- No tag: `0.0.1`, `0.0.2`, … (commit count on the branch).
- Tag `1.0.0` on a commit: that APK is `1.0.0`. Each later commit is `1.0.1`, `1.0.2`, …
- Official `versionName` (no suffix) only on `main` / `master` or a `v*` tag. A PR build is `1.0.1-PR.42` (the PR number). Other branches get `-PR`.
- `versionCode` is `major * 1000000 + minor * 10000 + patch` so a new tag still raises the integer phones compare.

CI clones the full history and tags so the APK matches local `./gradlew :app:assembleRelease`. Override with `-PversionName=1.2.3 -PversionCode=1002003` or `-PprNumber=42` if needed.

## Build

Requires JDK 17 and Android SDK (compileSdk 35).

```bash
cd apps/android
./gradlew :domain:test :app:assembleRelease
```
