# OpenCode Android

<p align="center">
  <img src="docs/screenshot.png" width="280" alt="OpenCode Android screenshot" />
</p>

<p align="center">
  <a href="https://github.com/yuga-hashimoto/opencode-android/actions/workflows/android.yml"><img src="https://github.com/yuga-hashimoto/opencode-android/actions/workflows/android.yml/badge.svg" alt="CI" /></a>
  <a href="https://github.com/yuga-hashimoto/opencode-android/releases/latest"><img src="https://img.shields.io/github/v/release/yuga-hashimoto/opencode-android" alt="Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/yuga-hashimoto/opencode-android" alt="License: MIT" /></a>
  <a href="https://github.com/yuga-hashimoto/opencode-android/releases/latest"><img src="https://img.shields.io/github/downloads/yuga-hashimoto/opencode-android/total" alt="Downloads" /></a>
</p>

**An unofficial, open-source Android client for [OpenCode](https://github.com/sst/opencode) — run AI coding agents on your phone or connect to a remote PC.**

OpenCode Android does not fork OpenCode. It uses the same REST/SSE API to talk to either an on-device runtime (Alpine Linux + OpenCode binary via PRoot) or a remote `opencode serve` instance on your PC/Mac/Linux.

> [!IMPORTANT]
> This is **not** an official OpenCode project.

[日本語のREADMEはこちら](README.ja.md)

---

## Features

- **On-device runtime** — Alpine Linux, Git, bash, curl, ripgrep, and OpenCode auto-installed on your Android device via PRoot
- **Remote connection** — Connect to OpenCode running on your PC/Mac/Linux over LAN or Tailscale
- **Runtime switching** — Seamlessly switch between local and remote execution, even mid-conversation (handoff)
- **Dynamic models** — Models, providers, and agents fetched live from your OpenCode instance
- **Session management** — Create, resume, rename, and delete sessions
- **Discovery** — Find PCs via QR code or mDNS (zero-config LAN discovery)
- **Real-time streaming** — SSE-based live responses, tool execution, and approval requests
- **Structured timeline** — Collapsible reasoning, tool calls, and command output
- **Approval flow** — Approve or reject dangerous tool operations
- **Voice input** — Push-to-talk with Android speech recognition + wake word detection
- **Text-to-speech** — Read responses aloud
- **Digital assistant** — Register as Android's default assistant (home gesture / corner swipe)
- **Secure storage** — Connection credentials encrypted with Android Keystore
- **Bilingual** — English and Japanese UI

## Screens

| Screen | Description |
|--------|-------------|
| Home | Current runtime, model, agent, recent sessions |
| Chat | Conversation with collapsible reasoning/tools, voice input, model switching, approvals, handoff |
| Workspaces | Local runtime, PC connections, working folders |
| History | Running tasks, pending approvals, sessions, event log |
| Settings | Home assistant, TTS, continuous conversation, configuration |

## Quick Start

### Option A: On-Device (no PC needed)

1. Install the APK from [Releases](https://github.com/yuga-hashimoto/opencode-android/releases/latest)
2. Open the app → **Workspaces → This Android device → Set up on this device**
3. Wait for the runtime to download and install (~2 min on a good connection)
4. Start chatting with your AI coding agent

### Option B: Remote PC

1. Start OpenCode on your PC:

```bash
OPENCODE_SERVER_PASSWORD='your-strong-password' \
  opencode serve --hostname 0.0.0.0 --port 4096 --mdns
```

2. Install the APK on your Android device
3. Open the app → **Workspaces → Add connection**
4. Enter your PC's IP (or use **LAN search** / **QR code** for auto-discovery)

```text
Name:     Mac mini
URL:      http://192.168.1.10:4096
Username: opencode
Password: your-strong-password
```

> Tailscale works too: `http://100.x.y.z:4096` or `http://your-mac.tailnet-name.ts.net:4096`

### QR Code Setup

Generate a QR code on your PC:

```bash
npx qrcode "opencode://connect?name=Mac%20mini&url=http%3A%2F%2F192.168.1.10%3A4096&username=opencode&password=your-password&insecure=true"
```

Then scan it from **Workspaces → Add via QR** in the app.

## Security

- **Never** expose port 4096 directly to the internet
- Use LAN or Tailscale for connectivity
- Use an HTTPS reverse proxy on public networks
- The app never auto-approves dangerous operations
- Plaintext HTTP on LAN requires explicit per-connection opt-in

## On-Device Runtime Details

The setup process (triggered from Workspaces):

1. Verifies the native PRoot runner bundled in the APK
2. Downloads Alpine Linux minirootfs from the official CDN
3. Downloads the OpenCode musl binary from GitHub Releases
4. Validates SHA-256 checksums for both
5. Extracts to a private app directory
6. Installs Git, bash, curl, ripgrep, and CA certificates inside Alpine
7. Starts the OpenCode server on `127.0.0.1:4097`
8. Switches the app to the local runtime

Pinned versions (updatable via app releases without OpenCode changes):

- Alpine Linux 3.24.1
- OpenCode 1.18.3
- Architectures: arm64-v8a, x86_64

## Handoff (Runtime Switching Mid-Conversation)

From the chat header menu → **Continue on another runtime** — the app generates a conversation summary prompt and sends it to the selected runtime, letting you pick up where you left off (e.g., start on-device while commuting, continue on your PC at home).

## Connecting to OpenCode Desktop

Add server config to `~/.config/opencode/opencode.json`:

```json
{
  "server": {
    "port": 4096,
    "hostname": "0.0.0.0",
    "mdns": true
  }
}
```

Restart the desktop app, then discover it from the Android app via **LAN search**.

## Building from Source

Requirements: JDK 17, Android SDK, Python 3, network access (first build only)

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Output APKs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

Install to device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Design Documents

- [OpenCode Android v2 Design](docs/superpowers/specs/2026-07-18-opencode-android-v2-design.md)
- [Initial MVP Plan](docs/superpowers/plans/2026-07-18-initial-mvp.md)
- [Local Runtime Design](docs/LOCAL_RUNTIME.md)

## Third-Party Software

Runtime generation reuses generic Termux package resolution/extraction logic redesigned for OpenCode, inspired by the MIT-licensed Hermes Agent Android implementation. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for details.

## License

[MIT](LICENSE)
