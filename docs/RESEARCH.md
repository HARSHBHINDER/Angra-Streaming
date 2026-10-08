# Mobile live-broadcast apps: research and reference pipeline

Research date: 2026-10-06. **Scope (corrected):** apps that run *on the phone* and broadcast the phone's gameplay/screen or camera **live to** a streaming platform. This replaces my earlier viewer-app (Netflix/Spotify) research; the old `streaming-pipeline/` folder is that wrong interpretation and can be deleted.

**Assumption you should confirm:** the five platforms are **YouTube, Twitch, Facebook, Kick, TikTok**, each with a top-5 for Android and a top-5 for iOS (50 slots).

## How to read this

- Confidence tags: **[High]** official help/docs/store page/GitHub API; **[Med]** consistent secondary sources; **[Low]** single blog or vendor page.
- Eligibility rules (subscriber/follower minimums) change often, and many blogs repeat outdated numbers. I cross-checked against official pages where I could reach them; each is tagged.
- "How their code is better" is only answerable for the **open-source** pieces (real code, real metrics). For closed apps I can only report disclosed features and known limits.

## 1. The honest headline: there are only ~10 real apps, not 50

Each platform has **one first-party app**; everything else is a small shared pool of third-party broadcaster apps. So the 50 slots are 5 native apps plus the same handful of third-party apps re-ranked per platform. Where a platform has fewer than five *verified* options, I say so instead of padding.

| App | Android | iOS | Screen/game capture | Destinations | Notes |
|---|---|---|---|---|---|
| **YouTube** (native) | yes | yes | yes ("Screencast") | YouTube | [High] |
| **Twitch** (native) | yes | yes | yes ("Stream Games") | Twitch | [High] |
| **Kick: Go Live** (native) | yes | yes | yes (screen share, dual camera) | Kick | [Med] 500k+ downloads, 4.5 stars/907 ratings, v2.14 |
| **TikTok** (native) | yes | yes | yes ("Mobile gaming" LIVE) | TikTok | [Med] |
| **Facebook** (native) | yes | yes | camera only (no game capture found) | Facebook | [Med] |
| **Streamlabs** | yes | yes | yes | Twitch, YouTube, Kick, Facebook, Instagram, custom RTMP | [High] iOS 3.9 stars (1.2K), v5.0.7, free + IAP $4.99-$189.99 |
| **PRISM Live Studio** (NAVER) | yes | yes | yes (camera/screen/VTuber) | YouTube, Facebook, Twitch, BAND, Kick | [Med] |
| **Larix Broadcaster / Screencaster** (Softvelum) | yes | yes | yes | any RTMP/RTMPS/SRT/RIST/WebRTC-WHIP/RTSP | [High] up to 3 simultaneous connections |
| **Omlet Arcade** | yes | unclear | yes | Omlet, Twitch, YouTube, Facebook + personal RTMP link | [Med] v1.104.3, Jan 2026 |
| **Moblin** | no | yes | yes (screen source) | Twitch-first; YouTube, Kick, Facebook, OBS | [High] open source MIT, 744 stars, pushed 2026-10-05 |
| **Mobile Game Streamer** | no | yes | yes | Twitch, YouTube, Kick, custom RTMP | [High] but tiny: 1 developer, v1.1, no ratings yet |
| **IRL Pro** | yes | (separate iOS app) | IRL-focused | SRTLA relay | [Med] |
| ~~Mobcrush~~ | dead | dead | | | [Med] discontinued |

OBS does not exist on iOS and its Android port is unreliable for live use **[Low]**.

## 2. Top five per platform, per OS

Ranking rule: (1) the native app first: zero setup, no stream key, platform-specific features; (2) multi-destination apps with *verified* support for that platform; (3) protocol depth and engineering quality; (4) OS-specific extras. "Native-only" gaps are marked.

### YouTube
| # | Android | iOS |
|---|---|---|
| 1 | **YouTube app**: official, Screencast option, Android 8.0+. Needs **50 subscribers** and a verified channel, no live restriction in 90 days **[High]** | **YouTube app**: same rules, iOS 8+ **[High]** |
| 2 | **Streamlabs** | **Streamlabs** |
| 3 | **PRISM Live Studio** | **PRISM Live Studio** |
| 4 | **Larix Broadcaster/Screencaster** (RTMPS, HEVC) | **Larix Broadcaster/Screencaster** |
| 5 | **Omlet Arcade** | **Moblin** (open source; YouTube supported) |

Note: older articles say screen-streaming needs 1,000 subscribers. The official help page says **50** for mobile live generally **[High]**; the 1,000 figure is legacy. Replays default to private under 1,000 subscribers. "Playables" (play a game mini-activity inside a vertical live) only in 22 listed countries **[High]**.

### Twitch
| # | Android | iOS |
|---|---|---|
| 1 | **Twitch app** "Stream Games": official; no overlays supported; cannot combine camera and screen at once; Android gets an overlay-permission chat bubble **[High]** | **Twitch app** "Stream Games" **[High]** |
| 2 | **Streamlabs** (overlays/alerts the native app lacks) | **Streamlabs** |
| 3 | **PRISM Live Studio** | **Moblin**: Twitch is its primary target; SRTLA bonding, HEVC up to 4K60 **[High]** |
| 4 | **Larix** | **PRISM Live Studio** |
| 5 | **Omlet Arcade** | **Larix** |

### Kick
| # | Android | iOS |
|---|---|---|
| 1 | **Kick: Go Live**: screen share, dual camera, picture-in-picture, chat **[Med]** | **Kick: Go Live** **[Med]** |
| 2 | **Streamlabs** | **Streamlabs** |
| 3 | **PRISM Live Studio** (Kick login added) | **PRISM Live Studio** |
| 4 | **Larix** (Kick uses per-account RTMPS URL + key; Larix can push any RTMPS) | **Moblin** |
| 5 | **Omlet Arcade** (personal RTMP link) | **Mobile Game Streamer** (explicit Kick support; unproven app) |

### Facebook
| # | Android | iOS |
|---|---|---|
| 1 | **Facebook app**: camera live only; I found no native game screen capture. Account must be **60+ days old with 100+ followers** (Page/pro profile) **[High]** | **Facebook app**: same |
| 2 | **Streamlabs** | **Streamlabs** |
| 3 | **PRISM Live Studio** | **PRISM Live Studio** |
| 4 | **Omlet Arcade** (Facebook explicit) | **Larix** |
| 5 | **Larix** | **Moblin** |

Facebook's dedicated Gaming app was folded into the main app; gaming streams use Live Producer + RTMPS software. Spec: H.264, RTMPS, 8-hour max **[High]**.

### TikTok (thin: honest result is one proven option)
| # | Android | iOS |
|---|---|---|
| 1 | **TikTok app** "Mobile gaming" LIVE (Android asks screen-record permission). **1,000 followers recommended**, 18+, some regions fewer **[Med: TikTok help pages, aggregated]** | **TikTok app** "Mobile gaming" **[Med]** |
| 2-5 | **Unverified:** Larix, PRISM, Streamlabs, Omlet *could* push to TikTok only via a custom RTMP key, and I could not confirm TikTok issues mobile-usable keys (keys come from LIVE Studio on PC). None of Streamlabs/PRISM's documented destination lists names TikTok **[Low]** | Same |

If TikTok matters, the real finding is: build the native flow's equivalent, not a list of five.

## 3. What they do under the hood (the pipeline)

```
capture            encode               transport              platform
Android: MediaProjection -> MediaCodec (H.264/HEVC, CBR, 2 s GOP, AAC)
iOS:     ReplayKit ext   -> VideoToolbox                       -> RTMP(S) | SRT | SRTLA | WHIP
                                                                 -> platform ingest -> transcode ladder -> CDN -> viewers
```

- **Platform ingest specs** (what the app must match): YouTube accepts H.264/H.265/AV1 over RTMP/RTMPS, CBR, **keyframe every 2 s (never over 4 s)**, AAC 128 kbps, and transcodes everything itself **[High]**. Twitch ingest is RTMP to `rtmp://<ingest>/app/<key>`, non-partner ceiling reported as 6,000 kbps **[Med]**; Twitch documents no SRT. Facebook: H.264 over RTMPS **[High]**. Kick: per-account RTMPS URL on port 443 **[Med]**.
- **First-party app transport is not public.** Nobody documents what the YouTube/Twitch/Kick apps send on the wire; only third-party apps document RTMP/SRT choices.
- **Android capture** (official docs **[High]**): `MediaProjection` -> `VirtualDisplay` -> encoder surface. Android 14+ needs a `mediaProjection` foreground service; **each capture needs fresh user consent** and the token is single-use; Android 14+ can share a single app window instead of the whole screen; Android 15 QPR1+ shows a status-bar chip to stop sharing. Frames are only produced when the screen changes, so encoders must force a constant fps (RootEncoder does this with `setForceRender`).
- **iOS capture:** ReplayKit **Broadcast Upload Extension**: a separate process that gets `CMSampleBuffer`s for video, app audio, mic audio **[High]**. Hard memory ceiling of about **50 MB** (one byte over and the OS kills it) **[Med: Apple forums, vendor docs]**; vendors recommend 720p, hardware H.264, 15-30 fps. Larix notes that screencast on iOS captures only mic audio unless the foreground app itself supports ReplayKit **[High]**.
- **Bonding:** Streamlabs ("Network Boost" Wi-Fi + cellular) and Moblin/IRL Pro/Belabox (SRTLA) combine connections for resilience. Streamlabs reviews mention audio delay and phone overheating **[Med]**.

## 4. "How their code is better": only the open-source pieces can be judged

| Project | Role | Evidence |
|---|---|---|
| **RootEncoder** (Android, Kotlin/Java) | RTMP/RTSP/SRT/UDP/WHIP(beta); MediaProjection screen streaming; H.264/H.265/AV1; Apache-2.0 | 3.0k stars, release 2.8.1 on 2026-09-01 **[High]** |
| **StreamPack** (Android, Kotlin) | RTMP/RTMPS/SRT; Camera2/MediaCodec; HEVC/AV1/VP9; Opus; Apache-2.0 | 367 stars, pushed 2026-10-05 **[High]** |
| **HaishinKit.swift** (iOS) | RTMP, SRT, WebRTC (WHIP/WHEP alpha); ReplayKit supported; BSD-3 | 3.1k stars, 2.2.5 on 2026-03-28; needs Xcode 26 / Swift 6 **[High]** |
| **Moblin** (iOS app) | Full production app, MIT, Swift: SRTLA bonding, HEVC to 4K60, adaptive bitrate, Apple Watch | 744 stars, pushed 2026-10-05 **[High]** |
| Larix Android SDK | sunset: no longer sold **[High]** | |

Takeaways: the best-engineered *shipping* mobile broadcaster whose code you can read is **Moblin** (iOS) and, as libraries, **RootEncoder** (Android) and **HaishinKit** (iOS). HaishinKit's own ReplayKit sample deliberately caps the video input queue at five frames "because ReplayKit is sensitive to memory": that is the key code-level lesson for iOS.

## 5. Reference pipeline (built)

`live-broadcast-pipeline/`:

```
Android app (RootEncoder: MediaProjection -> H.264/AAC -> RTMP)  ┐
iOS app (ReplayKit extension + HaishinKit -> RTMP)               ├-> ingest (local stand-in: ffmpeg RTMP listener)
Synthetic phone encoder (ffmpeg, same encoder settings)          ┘        -> ingest/verify.py gate
```

`verify.py` publishes a synthetic stream tuned to the YouTube spec (H.264 CBR, 2 s GOP, AAC 128k) over real RTMP into a local listener, then checks the recording: codecs, duration, keyframe spacing. Swap the URL to `rtmps://...` + your key to test a real platform.

### Verification status

| Piece | Status |
|---|---|
| Local ingest + synthetic publisher + gate | **Run and passing**: real RTMP, H.264 + AAC, 10.0 s received, 5 keyframes at exactly 2.00 s spacing (YouTube limit is 4 s) |
| Android full app (Compose UI + bubble + recording + channels): debug APK + lint | **Built, lint clean** (~28 MB debug APK; RootEncoder 2.8.1, Compose, Material3) |
| Android capturing a real screen to a real platform | **Not tested**: no emulator/device here |
| iOS app + broadcast extension (tabbed SwiftUI + channels + extension) | **Written, not compiled**: needs macOS + Xcode 26; code mirrors HaishinKit's official sample |

### Implemented features (v0.2, this session)

Both apps, driven by one shared channel/preferences model:
- **Channels**: add/edit/delete multiple destinations (YouTube, Twitch, Kick, Facebook, custom RTMP), pick the active one. Stream keys: Android **EncryptedSharedPreferences** (Keystore-backed); iOS rides the app group (test-only, Keychain in a shipping build).
- **Per-channel settings**: resolution (360p–1080p), fps (30/60), video bitrate (slider), audio bitrate, sample rate, stereo.
- **In-app Go Live / Stop** toggle; live state surfaced in the UI.
- **Local recording** to MP4 with its own resolution + audio settings; can record while streaming.
- **Stream-delay control** 0–10 min (two meters); buffered up to 60 s locally, longer defers to the platform.
- **Apple design style**: Android = Material3 themed iOS-like (grouped inset cards, segmented chips, capsule Go Live, SF-ish palette #007AFF/#FF3B30/#34C759); iOS = native SwiftUI `Form`/`TabView`.

**Android-only floating bubble**: draggable, icon **or** live timer (tap toggles), custom image, transparency slider to 95%. It appears only while live and vanishes on stop. iOS has no cross-app overlay, so it uses the OS indicator + (planned) Live Activity instead.

**Guardrail kept:** the OS screen-capture indicator (Android notification/chip, iOS red bar) stays visible regardless of bubble transparency — it is not suppressible by design, and this app does not try to.

**Deferred:** simultaneous multi-platform fan-out (needs a relay or heavy on-device multi-encode), OAuth account login (v0.2 uses stream-key entry), HEVC/AV1 toggle, true >60 s delay buffering, iOS Live Activity.
| GitHub Actions workflow | YAML validated only |
| Real platform ingest (YouTube/Twitch/...) | **Not tested**: needs your stream keys; I will not use credentials without your say-so |

## 6. Decisions for you

1. Confirm the five platforms (YouTube, Twitch, Facebook, Kick, TikTok), or swap (Instagram, Trovo, Rumble, Shorts?).
2. What do we build: a **multi-destination broadcaster** (Streamlabs-style: one app, many platforms), or a **native-feel single-platform** app? Multi-destination is the market gap given TikTok/Facebook lack good gameplay capture.
3. Transport: RTMPS only (works everywhere) or add **SRT/SRTLA bonding** (better on mobile networks, but only some platforms accept it, so you'd relay)?
4. Do you want overlays/alerts/chat (Streamlabs parity)? That is the big feature cost.
5. iOS needs a Mac or the GitHub macOS runner for every build.

## Sources

- [YouTube Help: mobile live stream eligibility](https://support.google.com/youtube/answer/9228390) and [YouTube encoder settings](https://support.google.com/youtube/answer/2853702)
- [Twitch Help: Mobile Game Broadcasting](https://help.twitch.tv/s/article/mobile-game-broadcasting?language=en_US) and [Twitch video broadcast docs](https://dev.twitch.tv/docs/video-broadcast/)
- [Meta: Facebook Live requirements](https://www.facebook.com/business/help/162540111070395), [How to go live on mobile](https://www.facebook.com/business/help/1884140525218868?id=1123223941353904), [Gaming on Facebook Live](https://www.facebook.com/business/help/2771226942969318)
- [TikTok: enable mobile gaming on LIVE](https://www.tiktok.com/discover/how-to-enable-mobile-gaming-on-live?lang=en)
- [Kick - Go Live app listing](https://mwm.ai/apps/app/6743709514)
- [Streamlabs on the App Store](https://apps.apple.com/us/app/streamlabs-live-streaming-app/id1294578643); [Mobile Game Streamer](https://apps.apple.com/np/app/mobile-game-streamer/id6748644959)
- [Larix Screencaster](https://softvelum.com/larix/screencaster/) and [Larix overview](https://softvelum.com/larix)
- [PRISM Live Studio coverage](https://blog.eklipse.gg/streaming-tips/best-mobile-live-streaming-apps.html)
- [Android MediaProjection docs](https://developer.android.com/media/grow/media-projection)
- [Apple ReplayKit RPBroadcastSampleHandler](https://developer.apple.com/documentation/replaykit/rpbroadcastsamplehandler); [Apple forum: broadcast extension limit](https://developer.apple.com/forums/thread/651367)
- GitHub: [RootEncoder](https://github.com/pedroSG94/RootEncoder), [StreamPack](https://github.com/ThibaultBee/StreamPack), [HaishinKit.swift](https://github.com/HaishinKit/HaishinKit.swift), [Moblin](https://github.com/eerimoq/moblin)
- [Streamscharts: best mobile apps for Twitch/YouTube](https://streamscharts.com/news/best-apps-twitch-and-youtube-streaming-your-mobile-phone)
