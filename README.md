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

