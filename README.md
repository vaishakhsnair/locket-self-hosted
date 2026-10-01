# Locket

Private photo delivery to an Android home-screen widget.

This repository is an Android-first private photo app plus a small self-hosted API. The current build captures photos locally, encrypts media on-device when configured, uploads to the configured server, keeps a date/time archive, exports images to Files, and provides a stealth home-screen widget.

## Layout

- `android/` — Kotlin/Compose app, CameraX capture, archive, export, stealth widget.
- `server/` — Go API with JSON metadata and local media storage.
- `deploy/` — Docker Compose and tunnel examples for a home server, NAS, Raspberry Pi, or VPS.

## Local server

```bash
cd server
go run ./cmd/locket
```

The server listens on `:8080`, stores data under `server/data`, and exposes `/healthz`. Set `LOCKET_DATA_DIR`, `LOCKET_ADDR`, and optionally `LOCKET_ACCESS_TOKEN`. If the token is set, enter the same value in the app’s Widget connection screen; `/healthz` remains public for health checks. The app can also use a shared photo key: AES-GCM is performed on-device and the server stores the resulting `LKT1` envelope. Both phones must use the same key. Keep using HTTPS because the access token and metadata still need transport protection.

## Android

Open `android/` in Android Studio and run the `app` configuration. For a local emulator, use `http://10.0.2.2:8080/` as the API URL. The app is usable without a server for camera/archive/widget preview; configure the server URL in the Widget tab to enable upload.

The Archive tab has a manual refresh action and the app also schedules a low-frequency WorkManager sync (Android controls the exact delivery window). When a server URL is configured, the app starts a low-priority foreground delivery service connected to the server’s self-hosted SSE stream; new-photo events trigger an immediate sync without FCM/ntfy. This keeps delivery independent of third-party push, at the cost of a persistent low-priority notification and some battery use. Clear the server URL to stop it.

The Widget tab can share a `locket://connect` setup link. Opening it on another installed device imports the server URL, optional access token, and shared media key, then starts delivery. Treat the link like a password because it contains the connection secrets.

## Exposure

The API is HTTP-only inside the compose network. Put it behind HTTPS when exposed outside a LAN. `deploy/cloudflared/config.yml` is an example outbound tunnel route; it does not require port forwarding.
