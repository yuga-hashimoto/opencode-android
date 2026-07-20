# Device validation matrix

Add a row for every device / API combination validated before tagging a public
stable release. CI rows are filled automatically; physical rows are manual.

| Device | API | ABI | Result | Date | Notes |
|--------|-----|-----|--------|------|-------|
| GitHub Actions x86_64 emulator | 26 | x86_64 | CI green | | `connectedDebugAndroidTest` |
| GitHub Actions x86_64 emulator | 34 | x86_64 | CI green | | `connectedDebugAndroidTest` |
| API 36 ARM64 emulator | 36 | arm64-v8a | Local smoke passed | 2026-07-19 | local runtime install + chat + diagnostics + delete |
| Xiaomi Android 16 (HyperOS) physical | 36 | arm64-v8a | Pending | | capture FGS battery + OEM-kill behavior |
| Pixel 8 physical | 34 | arm64-v8a | Pending | | |
| Older API 26 physical (e.g. Pixel 2) | 26 | arm64-v8a | Pending | | |

## Battery benchmark results

| Device | Idle drain (%, 30 min) | Chat drain (%, 5 min) | PRoot RSS (MB) | Runtime disk (MB) |
|--------|------------------------|-----------------------|----------------|-------------------|
| (fill from `scripts/battery_benchmark.sh`) | | | | |

Append new rows per release; keep historical data to spot regressions.
