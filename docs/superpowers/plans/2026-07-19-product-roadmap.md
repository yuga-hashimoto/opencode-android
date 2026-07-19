# OpenCode Android Product Roadmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Bring OpenCode Android from functional MVP to release-ready dual-runtime client across security, core UX, local practicality, quality, and open-source release.

**Architecture:** Single-module Compose app with RuntimeRegistry selecting Local (PRoot/Alpine/OpenCode) or Remote (`opencode serve`) backends over shared REST/SSE.

**Tech Stack:** Kotlin 1.9, Jetpack Compose, OkHttp, EncryptedSharedPreferences, PRoot + Alpine local runtime

## Global Constraints

- Do not fork OpenCode; use REST/SSE only
- Local server binds `127.0.0.1` only
- Cleartext HTTP only for localhost / LAN / Tailscale / `.local`
- Dangerous tools never auto-approved
- Prefer TDD for backend/runtime changes; keep unit tests green

## Phases

### P0 — Safety & reliability hotfixes
1. Network Security Config; remove global cleartext
2. Cache `LocalOpenCodeBackend` HTTP client by port
3. SSE repository backoff (API already has backoff)
4. Include truncated API error response bodies
5. Sync COMPLETION_CHECKLIST + LOCAL_RUNTIME docs

### P1 — Core UX
1. Activity approval actions
2. Permission `remember` maps to `always` when needed (API has no separate field)
3. POST_NOTIFICATIONS runtime request
4. Completion / failure / approval notifications with actions
5. Speech locale from system language

### P2 — Local practicality
1. Provider API key UI + secure storage + runtime env injection
2. SAF external workspaces
3. Home assistant runtime/model/workspace prefs
4. Capability / version badges from health

### P3 — Quality
1. i18n pass (hardcoded JP → resources)
2. Deduplicate RuntimeTarget delegates
3. Split large composables; SavedState where practical
4. Minimal Compose UI smoke tests if feasible
5. Dep cleanup (Jetifier, unused JitPack)
6. Tool/command cards in chat (stretch)

### P4 — Release
1. THIRD_PARTY_NOTICES.md
2. Signed release docs
3. Device matrix notes (document gaps honestly)
4. Security review notes
5. Checklist + README alignment

### P5 — Deferred
mDNS, QR, attachments, session handoff, wake-word pack, multi-module split
