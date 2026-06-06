# Changelog

## v0.1.16 - 2026-06-07

### Added

- Rebuilt the chat home screen with a brutalist visual language: 0 radius, 2px black borders, Space Grotesk display + monospace labels, paper canvas, and accent (blue) + done (lime) + danger (orange) status colors. The top bar (agent / model / plan / K value) and the four-cell status grid (表情 / 模型 / 终端 / 消息) now share the same brutalist dialect.
- Added a left-side role-card strip docked to the message list. Tap the strip to open the role-card picker; long-press to open the editor. The role card no longer crowds the top bar.
- Added a terminal launcher sheet (replaces the old USR popup and terminal sheet). The USR cell now opens a single sheet with a search box, quick action chips (ls / pwd / whoami / ps / df / free), filtered command history, and a mini terminal output panel that delegates to `vm.executeTerminalCommand`.
- Added a history search sheet (replaces the old history dialog). The MSG cell opens a brutalist search sheet with role filter chips (全部 / 用户 / 助手 / 工具) and date-grouped message results scoped to the current session.
- Added a plan-mode flow: selecting a plan mode and sending a message shows a "正在生成计划..." bubble animation in the chat while the plan is being generated. The plan book is then appended as an assistant message, and the composer shows three options (建议 / 取消 / 执行). Tapping any of the three dismisses the option row immediately to prevent accidental re-clicks. Executing first appends a "开始计划执行" acknowledgement to the chat, then runs the plan.
- Added tool call flow blocks for assistant messages with role "tool" (terminal_exec / terminal_python_install / file.write / etc.). Each block is a compact left-aligned chip with a triangle indicator, status badge, and time stamp. Default folded; long-press opens a detail sheet that shows the full call output plus the related note/think text. The "思考 (N 多少消息)" trace header is also long-pressable into a separate detail sheet.
- Added a long-press floating popup on assistant and user messages: 复制 / 重发 / 多选. Multi-select mode shows a top action strip with delete and cancel.
- Added a composable memory + undo banner for deletes. Snapshots are captured before delete via the new `vm.captureMessageSnapshots` method, and the Undo banner can restore the just-removed messages.
- Added image preview in the composer attachment row. Image attachments render as 64dp thumbnails via Coil; documents show as compact name chips.

### Improved

- The terminal mini overlay (floating widget) is now anchored to the bottom-right of the chat area by default. Drag is free (no long-press required). A small `×` button is provided in the chip header to hide the overlay without affecting the active task.
- The K value chip in the top bar now opens the compression threshold dialog on click (and the settings on long-press), so the user lands on the actual K value configuration.
- The trace flow block header now only toggles on the inline 展开/收起 button (the parent row no longer fights the button for tap events).
- Pending attachments can now be sent on their own: the send condition accepts `pendingAttachments.isNotEmpty()` even when the input is blank.
- All status-bar labels (LIVE/READY → 运行/就绪, EMOJI ON/OFF → 表情 开/关, MODEL → 模型, USR → 终端, MSG → 消息) are now in Chinese.
- User messages now sit on the right with a soft gray bubble (brutalist userBubble = `Color(0xFFEEEEEE)` light / `Color(0xFF2A2A2A)` dark), making them visually distinct from the white-paper agent bubbles on the left.

### Fixed

- Restored the previous 70 KB+ chat surface (lost in a 2026-06-06 UTF-8 / GBK encoding accident). The file `BrutalistHomeChatScreen.kt` is now self-contained: 33+ private composables covering surface, top bar, status grid, role card side strip, message list dispatch (bubble / tool / trace / planning bubble), trace + tool detail dialogs, composer, plan row, history search, terminal launcher, terminal mini overlay, terminal show chip, and all role-card / model / compression / suggestion pickers.

### Verified

- `compileDebugKotlin` passed (0 errors, deprecation warnings only).
- `assembleDebug` passed.
- `verifyTextEncoding` passed (no mojibake in source files).
- APK rebuilt at `app/build/outputs/apk/debug/app-debug.apk` (279 MB) and copied to the repo root as `LGClaw-Pro-debug.apk`.
## v0.1.11 - 2026-06-02

### Added

- Rebuilt the chat home screen with a MiniMax-style white-glass launchpad, compact feature groups, and a refined composer bar.
- Added compact bottom-sheet pickers for model switching and role-card binding, grouped by provider or role card.
- Added full trace/note extraction helpers so public `assistant_note` / `note` / `think` text can be shown cleanly in tool detail views.

### Improved

- Tool results now stay compact in the transcript as short pills like `terminal_exec [ok]`; long-pressing a tool opens the bottom detail sheet.
- Tool detail sheets now show note/think text, raw output, and attachments without flooding the message list.
- Composer, trace rows, tool pills, attachment chips, and planning controls follow the theme message font size.
- `attachment_send` now returns local path and media hints so agents can follow up with the `message` tool when sending images/files to remote channels.
- Hardened provider fallback, terminal scheduling, and empty-response recovery paths so long tasks are less likely to end with a blank assistant response.

### Verified

- Debug APK rebuilt as `LGClaw-Pro-debug.apk`.
- `compileDebugKotlin` passed.
- `testDebugUnitTest` passed.
- `assembleDebug` passed.

## v0.1.10 - 2026-06-02

### Added

- Added the local `AttachmentBridge` MVP for chat uploads and agent-generated files.
- Chat attachments are copied into app-private `files/attachments/` storage with a persistent JSON index.
- Added `AttachmentTools` so the agent can preview, list, read text attachments, and save generated text or binary files back into the current chat.
- Added FileProvider-backed attachment opening so users can tap local images and files safely from chat bubbles.

### Fixed

- Improved the compact file attachment card and Chinese attachment labels in the chat UI.
- Kept attachment support scoped to the chat bridge without changing model provider logic or remote channel file protocols.

### Verified

- `compileDebugKotlin` passed after adding AttachmentBridge.
- `assembleDebug testDebugUnitTest --stacktrace` passed.

## v0.1.9 - 2026-06-01

### Added

- Added the local `AttachmentBridge` MVP for chat uploads and agent-generated files.
- Chat attachments are copied into app-private `files/attachments/` storage with a persistent JSON index.
- Added `AttachmentTools` so the agent can preview, list, read text attachments, and save generated text or binary files back into the current chat.
- Added FileProvider-backed attachment opening so users can tap local images and files safely from chat bubbles.

### Fixed

- Reworked the embedded terminal runtime packaging so the bundled Termux-style toolchain can execute from LGClaw's private storage on Android.
- Added a safe terminal launch fallback: if the embedded shell is blocked, LGClaw reports the failure in Chinese instead of crashing the chat page.
- Fixed the mini terminal overlay close button so it hides the floating monitor without cancelling the running task.
- Improved the compact file attachment card and Chinese attachment labels in the chat UI.

### Verified

- Debug APK rebuilt as `LGClaw-Pro-debug.apk`.
- `compileDebugKotlin` passed after adding AttachmentBridge.
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
