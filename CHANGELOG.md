# Changelog

## v0.1.9 - 2026-06-01

### Fixed

- Reworked the embedded terminal runtime packaging so the bundled Termux-style toolchain can execute from LGClaw's private storage on Android.
- Added a safe terminal launch fallback: if the embedded shell is blocked, LGClaw reports the failure in Chinese instead of crashing the chat page.
- Fixed the mini terminal overlay close button so it hides the floating monitor without cancelling the running task.

### Verified

- Debug APK rebuilt as `LGClaw-Pro-debug.apk`.
- Confirmed APK contains `rootfs.zip`, `offline-debs.zip`, and `toolchain.zip`.
- `assembleDebug testDebugUnitTest lintDebug` passed.

## v0.1.8 - 2026-06-01

### Added

- Embedded offline arm64 terminal runtime with Node.js/npm, Python/pip/uv, Git, SSH, shell tools, and isolated workspaces.
- Codex-style agent loop planning that can inspect, plan, use skills/tools/terminal, verify, and then answer.

### Improved

- Terminal launch and long-press flows are hardened so first-run initialization failures no longer crash the chat page.
- GitHub README, Chinese README, and release-facing copy now point to the latest APK on GitHub Releases.

### Verified

- Debug APK rebuilt as `LGClaw-Pro-debug.apk`.
- `assembleDebug testDebugUnitTest lintDebug` passed.

## v0.1.7 - 2026-06-01

### Added

- Replaced the launcher icon, round icon, in-app mark, and splash artwork with the new LGClaw visual identity.
- Added a lightweight Compose startup animation that fades and scales the new LGClaw icon without affecting high-refresh scrolling.
- Added a calmer chat home header: the center title area is now intentionally blank, while quick actions remain available.

### Improved

- GitHub README and Chinese README now present the latest APK, feature story, visual identity, and update notes more clearly.
- Repository brand assets under `docs/assets/brand` now match the app icon used in the APK.

### Verified

- Debug APK is rebuilt as `LGClaw-Pro-debug.apk`.
- Android high refresh rate preference remains in `MainActivity`.

## v0.1.6 - 2026-06-01

### Added

- Modern white-glass panel system for sidebar features and settings pages.
- Responsive modern chat bubbles and compact tool result drawer.
- Theme presets, glass bubble tuning, role cards, multimodal upload flow, compression controls, and planning mode refinements.
