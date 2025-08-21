# ElevenLabs ConvAI for Android (Kotlin)

> **Status:** Alpha (API may change). Unofficial. Not affiliated with ElevenLabs.

Android-native Kotlin library for building **real-time voice agents** on top of **ElevenLabs Conversational AI** via the **WebSocket** transport. It focuses on Android idioms—**Coroutines/Flow**, **Audio Focus**, **background resilience**, **Bluetooth routing**, and a **pluggable audio pipeline**—so you don’t have to hand-roll networking/audio plumbing.

This project **complements** the official ElevenLabs SDKs (including Kotlin) by offering a **WebSocket-first** path with deeper Android audio control. If you need a **WebRTC/LiveKit** integration or cross-platform **React/React-Native**, the official SDKs may be a better fit.

---

## Why this exists

Most voice-agent SDKs optimize for the web or for cross-platform. Native Android apps often need things that are harder with those stacks:

- **Always-on, background-savvy voice agents** (drive mode, earbuds companions) with proper **Audio Focus** + **foreground services**.
- **Push-to-talk and barge-in** with low latency and deterministic mixing/ducking of other audio.
- **Tight OS integrations & device “client tools”** (toggle brightness/Wi-Fi, read battery, launch Activities) with typed messages.
- **Poor-network resilience** (cell ↔ Wi-Fi handoffs) with **custom backoff, reconnect, and state machine** control.
- **Bluetooth/Android Auto/specialized routing** where you may need to drop into native audio APIs.
- **Enterprise constraints** (signed-URL auth, no API keys on device, optional cert pinning, obfuscation, encrypted buffers).

If these are your needs, this library aims to be the Android-first choice.

---

## TL;DR

### What it does **today** (alpha)

- **WebSocket** client (OkHttp) with **signed-URL** auth (no API key on device)
- **Typed models** for the ConvAI stream (agent/user messages, audio chunks, transcripts, VAD score, tool results)
- **AudioTrack** playback with smoothing
- **Android Audio Focus** helper (handles calls/notifications; exposes `StateFlow`)
- **Network-aware reconnection** (loss/resume) and lightweight **state machine**
- Kotlin **Coroutines/Flow** event stream, structured logging, basic tests

> ✅ In short: an Android-idiomatic, WebSocket-first ConvAI client with audio output, reconnection, and typed events.

### What v1.0 will add

- End-to-end **microphone capture & streaming** (PCM/Opus) with **VAD gating** and **AEC/NS/AGC** options
- First-class session controls (**mute/unmute**, **feedback**, **barge-in**, **push-to-talk**)
- Opinionated **client-tool** helpers (invoke/serialize results) + dynamic variables utils
- Robust connection **state machine** with automatic **backoff/jitter**
- **Pluggable audio pipeline** (AudioRecord ↔ encoder ↔ WS) and modular sinks
- **Metrics hooks** (latency breakdown, underruns, reconnects) + structured logging
- **Sample apps** (Compose + Views), **Maven Central** artifacts, and **KDoc** site
- Clear **WebSocket vs WebRTC** guidance; optional WebRTC adapter (stretch)

---

## Where this fits (ecosystem)

- **Official ElevenLabs Conversational AI SDKs** exist for **Kotlin (Android)**, **Swift (iOS)**, **React/React-Native**, **Web/JS**, and **Python**. This project positions itself as **Android-native + WebSocket-first** for devs who want deeper control over audio routing, background services, and resilience.

    - Kotlin (official): https://elevenlabs.io/docs/conversational-ai/libraries/kotlin
    - React Native (LiveKit / dev builds, **not** Expo Go): https://elevenlabs.io/docs/conversational-ai/libraries/react-native
    - Libraries index: https://elevenlabs.io/docs/libraries

> If you’re on web/React/Expo or want WebRTC by default, start with the official SDKs. If you’re Android-native and want tight control and simpler WS plumbing, this library leans into that.

---

## Who should use this?

- **DriveCoach / Earbuds companion:** background agent with push-to-talk, **ducking** over Maps/Spotify, barge-in replies, and **Bluetooth** routing.
- **Interactive podcasts/audiobooks:** live “co-host” that interjects and ducks program audio with deterministic buffers.
- **Accessibility / notification assistant:** system-wide narration with barge-in and quick **client tools** (e.g., “brightness 20%”).
- **Field-ops / kiosk agents:** signed-URL auth, resilience across networks, optional cert pinning and encrypted buffers.
- **Labs/prototyping:** want **WS** + **AudioRecord/AudioTrack** tuning without pulling in a full WebRTC stack.

---

## Installation

Until artifacts are published to Maven Central, include the module directly:

```
root/
 ├─ settings.gradle.kts   // include(":elevenlabs-ws-kt")
 ├─ app/
 └─ elevenlabs-ws-kt/     // this library module
```

**Minimums:** Android 7.0 (API 24), Kotlin 1.9+

> Maven Central + versioned API are planned before v1.0.

---

## Quick start

```kotlin
val cfg = ElevenLabsConfig(
  signedUrlProvider = { signedUrlService.fetch() }, // your backend; returns short-lived WS URL
  sampleRateHz = 16_000,
)

val client = ElevenLabsClient(cfg)

client.messages.onEach { msg ->
  when (msg) {
    is AgentResponse       -> renderText(msg)
    is AudioChunkMessage   -> audioPlayer.enqueue(msg.pcm)
    is TranscriptMessage   -> updateTranscript(msg)
    is VadScoreMessage     -> ui.setVad(msg.score)
    is ClientToolResult    -> showToolResult(msg)
    else -> Unit
  }
}.launchIn(uiScope)

client.connect() // opens WebSocket via signed URL
```

See `SimpleConversationListener` and `AndroidAudioManager` for ready-made hooks.

---

## Push-to-talk & barge-in (planned v1.0)

```kotlin
// Hold-to-talk button
pttButton.setOnTouchListener { _, event ->
  when (event.action) {
    ACTION_DOWN -> client.session.mic.startPushToTalk()
    ACTION_UP   -> client.session.mic.stopPushToTalk()
  }
  true
}

// Barge-in: auto-duck current audio when agent speaks
client.session.events.onEach { e ->
  if (e is AgentPlaybackStarted) mediaDucker.duck()
  if (e is AgentPlaybackEnded)   mediaDucker.unduck()
}.launchIn(uiScope)
```

---

## Client tools (device integrations)

Register Android-side “client tools” and return typed results to the agent:

```kotlin
client.registerTool(
  name = "set_brightness",
  schema = mapOf("level" to JsonNumber(0..100))
) { params ->
  val level = (params["level"] as? Int)?.coerceIn(0, 100) ?: 50
  systemUi.setBrightness(level)
  ToolResult.success(mapOf("applied" to level))
}
```

Configure the matching tool in the ElevenLabs dashboard and the agent can invoke it.

---

## Auth pattern: **signed URL** (recommended)

Never ship long-lived API keys on device. Instead:

1) Your backend exchanges its API key for a **short-lived conversation token** or **signed WS URL**.
2) The app requests that URL, then connects directly over WebSocket.

```ts
// Example Node/Express route (server)
app.get('/eleven/signed-url', authn, async (req, res) => {
  const r = await fetch(\`https://api.elevenlabs.io/v1/convai/ws/signed-url?agent_id=\${AGENT_ID}\`, {
    headers: { 'xi-api-key': process.env.ELEVEN_API_KEY }
  })
  if (!r.ok) return res.sendStatus(500)
  const { url } = await r.json()
  res.json({ url }) // expires in minutes
})
```

---

## Feature overview

### Transport & models
- OkHttp **WebSocket** with heartbeat/ping-pong
- Full **send/receive** schema via Kotlinx Serialization
- Lightweight **state machine** + sealed state enums

### Audio
- `AudioTrack` sink with buffer smoothing & underrun guards
- Planned `AudioRecord` mic capture with **VAD**, **AEC/NS/AGC**
- **Audio Focus** helper, ducking utilities, and exposure as `StateFlow`

### Resilience & DevX
- Network callbacks → **backoff reconnect**, error taxonomy
- Structured logging, correlation IDs
- Hooks for **metrics** (latency, queue depth, reconnects)

### Security & privacy
- **Signed-URL** auth; **no API key on device**
- Optional **certificate pinning**
- R8/ProGuard guidance; optional encrypted on-disk audio buffers

---

## Comparison (high-level)

| Capability | This library (now) | This library (v1.0) | Official Kotlin | React Native (Expo/LiveKit) | Web/React |
|---|---|---:|---:|---:|---:|
| Transport | **WebSocket** | WS + (optional) WebRTC (stretch) | **WebRTC** | **WebRTC (LiveKit)** | WS/WebRTC |
| Mic capture | — | ✅ `AudioRecord` + VAD/AEC | ✅ | ✅ | browser |
| Audio output | ✅ `AudioTrack` | ✅ pluggable sinks | ✅ | ✅ | ✅ |
| Session controls | — | ✅ mute/feedback/PTT/barge-in | ✅ | ✅ | ✅ |
| Client tools helpers | — | ✅ | ✅ | ✅ | ✅ |
| Android **Audio Focus** | ✅ basic | ✅ richer | OS-managed | N/A (RN) | N/A |
| Reconnect/backoff | ✅ basic | ✅ advanced | framework-managed | framework-managed | browser |
| Background patterns | helpers | helpers | app-specific | RN/Expo constraints | browser |

> See official docs for platform specifics (Kotlin: link above; RN uses LiveKit and **requires dev builds; not Expo Go**).

---

## Roadmap → v1.0

**Sprint 1 — Foundation (Weeks 1–2)**
- Package/API cleanup, DI boundaries, error taxonomy, structured logging
- Unit tests for parsing, reconnection, audio queueing

**Sprint 2 — Core features (Weeks 3–4)**
- Mic pipeline (AudioRecord), PCM/Opus encode, VAD, AEC/NS/AGC
- Session controls (mute/unmute, PTT, barge-in), feedback
- Client Tools helpers & dynamic variables
- Robust state machine (Disconnected ↔ Connecting ↔ Connected ↔ Recovering)

**Sprint 3 — Polish (Weeks 5–6)**
- KDoc + guides (signed URLs, Audio Focus, background)
- Integration tests (MockWebServer; audio golden files)
- Perf: latency/jitter minimization; buffer tuning
- Security audit (pinning, ProGuard/R8, encrypted caches)

**Sprint 4 — Release (Weeks 7–8)**
- Sample apps (Compose + Views) demonstrating tools, VAD, PTT
- CI/CD (Gradle cache, Android matrix, detekt/ktlint, jacoco)
- Publish to **Maven Central**
- Website/README refresh + tutorial

**Stretch**
- WebRTC adapter or “WS vs WebRTC” doc
- OpenTelemetry exporter for metrics
- Bluetooth routing helpers; foreground service helpers
- ExoPlayer output sink for advanced mixing

---

## Samples (planned)

1. **Hello Agent** — push-to-talk, transcripts, VAD meter
2. **Tool Calling** — device brightness/battery client tools
3. **Streaming STT + TTS** — mic → agent → TTS playback with barge-in

---

## Contributing

PRs and issues welcome. Expect some churn until v1.0. See `TESTING.md` for guidance.

---

## License

**MIT** — see [`LICENSE`](./LICENSE).

---

## Trademark

“ElevenLabs” is a trademark of ElevenLabs. This project is unaffiliated.

---
