# ElevenLabs ConvAI for Android (Kotlin)

> **Status:** In active development (alpha). API subject to change.

Android‑native Kotlin library for building **real‑time voice agents** on top of **ElevenLabs Conversational AI** via the **WebSocket transport**. It focuses on Android idioms (coroutines/Flow, audio focus, offline handling) so you can ship native apps without hand‑rolling networking and audio plumbing.

**Not affiliated with ElevenLabs**. Complements their official SDKs.

---

## TL;DR

### What this project does **now**

* WebSocket client built on OkHttp with **signed‑URL** auth
* Typed models for the ConvAI message stream (agent/user messages, audio chunks, transcripts, VAD score, tool results, etc.)
* **AudioTrack** playback with buffering
* **Android Audio Focus** helper (handles calls/notifications)
* **Network‑aware reconnection** (loss/resume)
* **Kotlin Coroutines + Flow** event stream
* Simple listener interface and lightweight state machine

> ✅ In short: an Android‑idiomatic, WebSocket‑first ConvAI client with audio playback, reconnection, and typed events.

### What this project will do **after the roadmap** (v1.0)

* End‑to‑end **microphone capture & streaming** (PCM/Opus), with **VAD gating** and **AEC/NS/AGC** options
* First‑class **session controls** (mute/unmute, feedback, barge‑in, push‑to‑talk)
* Opinionated **client‑tool helpers** (invoke/serialize results) and **dynamic variables** utils
* Robust **connection state machine** with automatic **backoff/jitter**
* Pluggable **audio pipeline** (AudioRecord ↔ encoder ↔ WS), modular **AudioTrack** sink
* **Metrics hooks** (latency breakdown, underruns, reconnects), structured logging
* **Sample apps** (Compose + classic Views) and **instrumented tests**
* **Maven Central** artifacts + **KDoc** site + versioned **SemVer** API
* Clear **WebSocket vs WebRTC** guidance; optional WebRTC adapter (stretch)

> 🚀 Goal: a production‑ready Kotlin SDK that covers the common Android needs **missing** from web/React‑centric stacks.

---

## Where this fits (ecosystem)

* ElevenLabs currently ships official libraries for **JavaScript (web)**, **React**, **React Native (Expo/LiveKit)**, **Python**, and **Swift (iOS)** — there is **no official Kotlin/Android SDK** as of Aug 2025. This library fills that gap for **native Android**.

    * Libraries index: [https://elevenlabs.io/docs/libraries](https://elevenlabs.io/docs/libraries)
    * React Native (Expo/LiveKit): [https://elevenlabs.io/docs/conversational-ai/libraries/react-native](https://elevenlabs.io/docs/conversational-ai/libraries/react-native)
    * Swift (iOS): [https://github.com/elevenlabs/ElevenLabsSwift](https://github.com/elevenlabs/ElevenLabsSwift)

This project is Android‑first and **WebSocket**‑first. If you’re on web or Expo RN, consider the official SDKs above.

---

## Installation

Until v0.1 is on Maven Central, add the module directly:

```text
root/
 ├─ settings.gradle.kts  // include(":elevenlabs-ws-kt")
 ├─ app/
 └─ elevenlabs-ws-kt/    // this library module
```

Minimums: **Android 7.0 (API 24)**, Kotlin **1.9+**.

---

## Quick start

```kotlin
val cfg = ElevenLabsConfig(
    signedUrlProvider = { signedUrlService.fetch() }, // your backend
    sampleRateHz = 16000,
)

val client = ElevenLabsClient(cfg)

client.messages.onEach { msg ->
    when (msg) {
        is AgentResponse -> renderText(msg)
        is AudioChunkMessage -> audioPlayer.enqueue(msg.pcm)
        is TranscriptMessage -> updateTranscript(msg)
        is VadScoreMessage -> ui.setVad(msg.score)
        is ClientToolResult -> showToolResult(msg)
        else -> Unit
    }
}.launchIn(uiScope)

client.connect() // opens WS via signed URL
```

See `SimpleConversationListener` and `AndroidAudioManager` for ready‑made hooks.

---

## Current feature set (alpha)

* **Transport:** OkHttp WebSocket + Ping/Pong + heartbeat
* **Auth:** short‑lived **signed URL** flow (no API key on device)
* **Models:** full message schema (send/receive) using Kotlinx Serialization
* **Audio output:** `AudioTrack` with buffer smoothing; stream format helpers
* **Audio focus:** request/abandon focus; state exposed as `StateFlow`
* **Resilience:** network callbacks → backoff reconnect; error taxonomy
* **DevX:** structured logger; sealed state enums; unit tests for core helpers

### Known gaps (alpha)

* **No** built‑in **mic capture** or **Opus/PCM encoder** yet
* Missing **convenience controls** (mute/unmute, feedback) and **client‑tool** helpers
* No **WebRTC** transport
* Limited docs; no sample app published; not yet on Maven Central
* CI is basic; code coverage under target; API may change

---

## Roadmap (→ v1.0)

This plan merges the internal *Project Excellence* sprints with additional Android‑specific items.

### Sprint 1 — Foundation (Weeks 1–2)

* Housekeeping: package naming, API surface, DI boundaries
* **Error taxonomy** & propagation (recoverable vs terminal)
* **Structured logging** (tags, correlation IDs)
* **Unit tests** for parsing, reconnection, audio queueing

### Sprint 2 — Core features (Weeks 3–4)

* **Microphone capture** pipeline (`AudioRecord`) with backpressure
* **PCM/Opus** encode + streaming, configurable frame sizes
* **VAD** gating; **AEC/Noise Suppression/AGC** toggles (where supported)
* **Session controls:** mute/unmute, push‑to‑talk, barge‑in; **feedback** API
* **Client Tools:** helpers for invocation + result messages; **dynamic variables** utils
* **Robust state machine** (Disconnected ↔ Connecting ↔ Connected ↔ Recovering)

### Sprint 3 — Polish (Weeks 5–6)

* **KDoc** + guides (signed URLs, audio focus, background)
* **Integration tests** (MockWebServer; audio golden files)
* **Performance**: latency and jitter minimization; buffer tuning
* **Security audit**: API key handling, cert pinning, ProGuard/r8, encrypted caches

### Sprint 4 — Release (Weeks 7–8)

* **Sample apps** (Compose + Views) demonstrating tools, VAD, push‑to‑talk
* **CI/CD** (Gradle cache, Android matrix, detekt/ktlint, jacoco)
* **Publish** to **Maven Central** with signed artifacts
* **Website/README** refresh + tutorial/blog post

### Stretch / nice‑to‑have

* **WebRTC adapter** (parity with RN where useful) or clear WS‑vs‑WebRTC doc
* **Metrics hooks** and optional `DropWizard`/`OpenTelemetry` exporter
* **Bluetooth routing** helpers; **foreground service** for background audio
* **ExoPlayer** output sink for advanced mixing

---

## Feature parity & gaps vs official SDKs

*(high‑level; subject to change — check vendor docs)*

| Capability                        | This library (now) | This library (planned v1.0) | React Native (Expo/LiveKit) | Swift (iOS)          | Web/React               |
| --------------------------------- | ------------------ | --------------------------- | --------------------------- | -------------------- | ----------------------- |
| Transport                         | WebSocket          | WS + optional WebRTC        | WebRTC via LiveKit          | WS/WebRTC (per docs) | Web (getUserMedia + WS) |
| Mic capture                       | —                  | ✅ `AudioRecord` + VAD/AEC   | ✅                           | ✅ `startRecording()` | ✅                       |
| Audio output                      | ✅ `AudioTrack`     | ✅ pluggable sinks           | ✅                           | ✅                    | ✅                       |
| Session controls (mute, feedback) | —                  | ✅                           | ✅                           | ✅                    | ✅                       |
| Client tools helpers              | —                  | ✅                           | ✅                           | ✅                    | ✅                       |
| Android audio focus               | ✅                  | ✅ richer                    | n/a (RN)                    | n/a (iOS)            | n/a                     |
| Reconnect/backoff                 | ✅ basic            | ✅ advanced                  | framework‑managed           | OS‑managed           | browser                 |
| Samples                           | WIP                | ✅                           | ✅                           | (examples)           | ✅                       |
| Distribution                      | source module      | **Maven Central**           | npm                         | SPM                  | npm                     |

Official docs: [https://elevenlabs.io/docs/libraries](https://elevenlabs.io/docs/libraries)

---

## Design & architecture

* **Coroutines/Flow** for async streams
* Layered modules: `core` (transport/state), `audio` (in/out), `api` (config & models)
* **Sealed models** mirror ConvAI messages; JSON via Kotlinx Serialization
* Clean separation between **transport** and **audio** for testability

---

## Performance targets

* **< 100 ms** added output latency (device‑local)
* Stable **10–20 ms** audio frames; no underruns on mid‑range devices
* Resilient under network handoffs (cell ↔ Wi‑Fi)

---

## Security & privacy

* **Signed‑URL** auth; never ship API keys in the app
* Optional **certificate pinning** on WS
* Obfuscation via **R8/ProGuard**
* Optional encrypted on‑disk audio buffer

---

## Samples (planned)

1. **Hello Agent** — push‑to‑talk, transcripts, VAD meter
2. **Tool Calling** — device brightness/battery client tools
3. **Streaming STT + TTS** — mic → agent → TTS playback with barge‑in

---

## Contributing

Issues and PRs welcome. Until v1.0, expect some churn. See `TESTING.md` for guidance.

---

## License

Apache 2.0

---

## Trademark

“ElevenLabs” is a trademark of ElevenLabs. This project is unaffiliated.
