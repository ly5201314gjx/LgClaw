<div align="center">
  <img src="./docs/assets/brand/lgclaw-removebg-preview.png" alt="LGClaw" width="86" />
  <h1>LGClaw</h1>
  <p><strong>运行在 Android 手机上的 AI 智能体工作台。</strong></p>
  <p>聊天、计划、工具、技能、记忆、角色卡、智能体、附件、多模型切换和主题定制，都放进一个移动端 AI 系统里。</p>
</div>

<div align="center">

[![Android](https://img.shields.io/badge/平台-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![本地优先](https://img.shields.io/badge/本地优先-智能体运行时-0A84FF?style=for-the-badge)](#)
[![APK](https://img.shields.io/badge/下载-LGClaw--Pro--debug.apk-f39c12?style=for-the-badge&logo=android&logoColor=white)](./LGClaw-Pro-debug.apk)

</div>

<p align="center">
  <a href="./README.md">English</a>
</p>

## 项目简介

LGClaw 是一个基于 PalmClaw / OpenClaw 移动智能体思路深度魔改的 Android AI 应用。它把手机变成一个本地 AI 指挥中心：模型供应商、对话、技能、工具、记忆、角色卡、智能体、计划模式、附件识别和自动化式工作流，都可以在手机上直接操作。

这个版本重点强化了中文 UI、智能体运行时扩展、计划模式、角色卡、压缩记忆、附件发送、主题自定义和移动端使用体验，目标不是做一个简单聊天壳，而是做一个能长期使用的 AI 工作台。

## 亮点功能

- **智能体中心**：新建、查看、绑定、解绑、测试、AI 补全智能体资料。
- **角色卡系统**：可定义角色设定，绑定后每轮对话都会读取角色卡，让 AI 按设定稳定扮演。
- **计划模式**：支持快速、标准、深度、Codex 调度式计划。先生成计划书，再由用户选择执行、补充要求或取消。
- **技能系统**：技能可开启/关闭，关闭后不会从列表消失；长按并二次确认才会删除。
- **动态工具系统**：运行时创建工具，下一轮对话即可让 AI 调用，不需要重新打包 APK。
- **压缩记忆**：本地 Codex 风格压缩算法，使用 TextRank 类句子评分生成摘要，并用 gzip 归档原文。
- **附件对话**：支持图片、PDF、Word 和多种文件。支持视觉的模型会收到图片内容；不支持读图的模型会明确提示。
- **模型控制台**：可配置供应商、拉取模型、一个供应商装备多个模型，并在聊天顶部快速切换。
- **搜索增强**：内置多搜索源与浏览器 fallback，提升联网搜索稳定性。
- **主题工作台**：可调整字体、文字颜色、气泡样式、聊天背景、侧边栏背景、透明度、模糊度和玻璃效果。
- **120Hz 适配**：窗口会请求设备支持的最高刷新率，让滚动和动画更顺。

## APK 下载

仓库中已包含当前构建好的调试 APK：

```text
LGClaw-Pro-debug.apk
```

在 GitHub 页面打开该文件即可下载，也可以克隆仓库后从本地安装。

> 这是 debug APK，适合测试和自用。正式公开分发建议使用自己的 keystore 重新签名发布。

## 从源码构建

环境要求：

- Android Studio
- JDK 17
- Android SDK，并在 `local.properties` 中正确配置

构建与测试：

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --stacktrace
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 仓库结构

```text
LGClaw/
  app/src/main/java/com/lgclaw/
    agent/          智能体循环与上下文组装
    agents/         智能体 Profile、角色绑定、运行时上下文
    memory/         长期记忆与压缩记忆
    providers/      OpenAI/Anthropic/Responses 等兼容供应商
    skills/         技能加载与匹配
    tools/          Android、本地文件、搜索、网页、动态工具等
    ui/             Compose 对话、设置、侧边栏、主题、面板
  app/src/main/assets/
    skills/         内置技能
    templates/      AGENT / USER / TOOLS / MEMORY / HEARTBEAT 模板
  docs/assets/      品牌与文档资源
  LGClaw-Pro-debug.apk
```

## 隐私与安全

- LGClaw 默认把应用数据保存在手机本地。
- 只有当你配置模型供应商、外部渠道或显式调用工具时，相关数据才会发送到对应服务。
- API Key 由用户在应用内自行填写和保存。
- AI 智能体只能使用运行时暴露的工具，以及用户授予的 Android 权限。
- debug APK 适合测试，不建议直接作为正式生产版本分发。

## 致谢

LGClaw 参考并延续了 PalmClaw / OpenClaw 风格的移动端智能体方向，在此基础上加入了大量中文 UI、智能体、技能、工具、记忆、计划模式、角色卡和主题系统改造。

## 许可证

见 [LICENSE](./LICENSE) 与 [LICENSE-COMMERCIAL.md](./LICENSE-COMMERCIAL.md)。
