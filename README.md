<div align="center">

# Angra Streaming

Open-source mobile game/screen broadcaster for Android — stream your phone to YouTube, Twitch, Kick, Facebook or any custom RTMP, with a floating control bubble, facecam, overlays and recording. Light (~4 MB), no watermark, free.

[![Download latest APK](https://img.shields.io/badge/Download-Latest%20APK-brightgreen?style=for-the-badge&logo=android)](../../releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

**[⬇ Download the latest version](../../releases/latest)** · **[All versions & changelogs](../../releases)**

</div>

## Download

The green **Download** button always points to the newest release and refreshes automatically — every time you publish a new version on GitHub, it serves that build. The [Releases page](../../releases) lists every version with its description, features and changelog, each with its own download. Pick whichever variant you want.

Direct latest APK: `https://github.com/HARSHBHINDER/Angra-Streaming/releases/latest/download/app-release.apk`

## Install (sideload)

1. Download `app-release.apk` from the latest release.
2. On your phone, allow **Install unknown apps** for your browser/file manager.
3. Open the APK and install.

## Features

| | |
|---|---|
| **Destinations** | Multiple channels: YouTube, Twitch, Kick, Facebook, custom RTMP/RTMPS. Stream keys stored encrypted (Android Keystore). |
| **Per-channel encoding** | Resolution 360p–1080p, 30/60 fps, orientation, H.264/HEVC, keyframe interval, video bitrate (CBR, 100-kbps steps), audio bitrate, sample rate, stereo. |
| **Facecam** | Front-camera picture-in-picture, size + corner selectable. |
| **Overlay** | Per-channel image or animated GIF (PNG/JPG/WebP/GIF), composited on the stream. |
| **Floating bubble** | Draggable, icon or live timer, custom image, transparency up to 95%. |
| **Stream delay** | OBS-style delay per channel, 0 s–10 min. |
| **Recording** | Record to MP4 while streaming. |
| **Reliability** | Adaptive bitrate, live health meter, audio-source + mic-device selection. |

The system screen-recording indicator stays visible whenever streaming — by OS design.

## Build from source

Needs JDK 17 + Android SDK (Android Studio's bundled JBR works).

```bash
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # signed release (needs keystore.properties, see below)
```

### Release signing

Create `keystore.properties` in the repo root (gitignored — never commit it):

```properties
storeFile=angra-release.jks
storePassword=••••
keyAlias=angra
keyPassword=••••
```

Generate the keystore once:

```bash
keytool -genkeypair -v -keystore app/angra-release.jks -alias angra \
  -keyalg RSA -keysize 2048 -validity 10000
```

## Releasing on GitHub

Push a tag and CI builds + publishes the signed APK automatically:

```bash
git tag v0.2.0
git push origin v0.2.0
```

GitHub Desktop: History → right-click the latest commit → **Create Tag…** (e.g. `v0.2.1`) → **Push origin**.

Optional repository **secrets** (Settings → Secrets → Actions) keep the same signature across releases; without them CI signs with a fresh throwaway key each release:
`KEYSTORE_BASE64` (base64 of `angra-release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

```bash
base64 -w0 app/angra-release.jks     # paste output as KEYSTORE_BASE64
```

## License

MIT — free for anyone. See [LICENSE](LICENSE).
