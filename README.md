<div align="center">
  <img src="./docs/assets/brand/lgclaw-removebg-preview.png" alt="LGClaw" width="86" />
  <h1>LGClaw</h1>
  <p><strong>A mobile-first AI agent workspace for Android.</strong></p>
  <p>Chat, plan, use tools, manage skills, bind agents, remember context, and run an extensible AI assistant directly on your phone.</p>
</div>

<div align="center">

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![Local First](https://img.shields.io/badge/Local--First-Agent%20Runtime-0A84FF?style=for-the-badge)](#)
[![APK](https://img.shields.io/badge/Download-LGClaw--Pro--debug.apk-f39c12?style=for-the-badge&logo=android&logoColor=white)](./LGClaw-Pro-debug.apk)

</div>

<p align="center">
  <a href="./README.zh-CN.md">简体中文</a>
</p>

## What Is LGClaw?

LGClaw is a heavily customized Android AI agent app based on the PalmClaw/OpenClaw mobile-agent idea. It turns a phone into a local AI command center: one place for model providers, conversations, skills, tools, memory, role cards, agent profiles, planning, attachments, and automation-style workflows.

This build focuses on a richer Chinese-first user experience, stronger runtime extensibility, and practical mobile controls that feel usable in daily work rather than just a demo.

## Highlights

- **Agent Center**: create, inspect, bind, unbind, test, and AI-complete runtime agents.
- **Role Cards**: define roleplay/persona cards and bind them to conversations so every turn reads the active card.
- **Plan Mode**: choose quick, standard, deep, or Codex-style planning. LGClaw generates a plan first, then waits for Execute, Add Requirements, or Cancel.
- **Skill System**: local skills can be enabled or disabled without disappearing; deletion requires long press and confirmation.
- **Dynamic Tools**: create runtime tools and expose them to the AI loop without rebuilding the APK.
- **Memory Compression**: local Codex-style memory compression with TextRank-like sentence scoring plus gzip archival.
- **Attachment Chat**: send images, PDFs, Word documents, and other files. Vision-capable models receive image content; non-vision models clearly report unsupported vision.
- **Provider Console**: configure providers, fetch models, equip multiple models per provider, and quickly switch models in chat.
- **Search Tools**: multiple search engines and browser fallback paths for more resilient web lookup.
- **Theme Studio**: customize text color, fonts, message bubbles, chat background, sidebar background, blur, opacity, and glass effects.
- **120Hz-Friendly UI**: the Android window requests the highest supported refresh mode for smoother scrolling and animation.

## Download

The debug APK built from this workspace is included for quick testing:

```text
LGClaw-Pro-debug.apk
```

On GitHub, open the file and download it, or clone the repository and install from the local copy.

> This APK is a debug build. For public distribution, create a signed release build with your own keystore.

## Build From Source

Requirements:

- Android Studio
- JDK 17
- Android SDK configured through `local.properties`

Build and test:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --stacktrace
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Repository Layout

```text
LGClaw/
  app/src/main/java/com/lgclaw/
    agent/          Agent loop and context assembly
    agents/         Agent profiles, role bindings, runtime context
    memory/         Long-term memory and compressed memory
    providers/      OpenAI-compatible, Anthropic-compatible, Responses providers
    skills/         Skill loading and matching
    tools/          Android, file, web, search, dynamic, and workflow tools
    ui/             Compose chat UI, settings, panels, theme, onboarding
  app/src/main/assets/
    skills/         Bundled skills
    templates/      AGENT / USER / TOOLS / MEMORY / HEARTBEAT templates
  docs/assets/      Brand and documentation assets
  LGClaw-Pro-debug.apk
```

## Privacy And Safety

- LGClaw stores app data locally on the phone unless a configured provider, channel, or tool explicitly sends data out.
- Model API keys are user-supplied and saved through the app configuration flow.
- The AI agent can use only the tools exposed by the runtime and Android permissions granted by the user.
- Debug APKs are convenient for testing but should not replace a signed production release.

## Credits

LGClaw is a customized Android AI agent project inspired by PalmClaw/OpenClaw-style mobile agent workflows, with extensive runtime, UI, memory, tool, skill, role-card, and planning enhancements.

## License

See [LICENSE](./LICENSE) and [LICENSE-COMMERCIAL.md](./LICENSE-COMMERCIAL.md).
