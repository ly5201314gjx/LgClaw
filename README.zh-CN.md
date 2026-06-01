<div align="center">
  <img src="./docs/assets/brand/lgclaw-removebg-preview.png" alt="LGClaw" width="112" />
  <h1>LGClaw</h1>
  <p><strong>把一台 Android 手机，变成更好看、更顺手、也更能长期工作的 AI 智能体工作台。</strong></p>
  <p>聊天、读图、计划、工具、技能、记忆、角色卡、智能体、主题和多模型切换，都收进一个更舒服的移动端 AI 系统里。</p>
</div>

<div align="center">

[![平台](https://img.shields.io/badge/平台-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![本地优先](https://img.shields.io/badge/本地优先-Agent%20Runtime-0A84FF?style=for-the-badge)](#)
[![下载 APK](https://img.shields.io/badge/下载-v0.1.8%20APK-f39c12?style=for-the-badge&logo=android&logoColor=white)](https://github.com/ly5201314gjx/LgClaw/releases/latest/download/LGClaw-Pro-debug.apk)

</div>

<p align="center">
  <a href="./README.md">English</a> ·
  <a href="https://github.com/ly5201314gjx/LgClaw/releases/latest/download/LGClaw-Pro-debug.apk">直接下载 APK</a> ·
  <a href="https://github.com/ly5201314gjx/LgClaw/releases">GitHub Releases</a>
</p>

![LGClaw cover](./docs/assets/site/demos/cover-lgclaw.png)

## 最新更新

**v0.1.8** 让 LGClaw 更像一台真正可用的移动端智能体工作台：

- 新增内嵌 arm64 终端运行时，离线提供 Node.js/npm、Python/pip/uv、Git、SSH、shell 工具和会话级工作区。
- 终端模式做了稳定性加固，长按、展开、取消、强制关闭都不会轻易把初始化流程带崩。
- 计划模式和 Agent 调度链路改成 Codex 风格：先规划，再挑技能、工具和终端步骤，执行后读取结果并修正，再给出结论。
- 最新安装包已发布到 [GitHub Releases](https://github.com/ly5201314gjx/LgClaw/releases/latest)。

完整记录见 [CHANGELOG](./CHANGELOG.md)。

## 为什么做 LGClaw

很多 AI App 还是一个聊天框：你问，它答，然后上下文慢慢丢掉，能力也只停在模型本身。

LGClaw 想做的，是另一种感觉。它让手机里的 AI 不只是“回答问题”，而是能带着角色、记忆、技能和工具一起工作。它可以先写计划，再等你确认；可以绑定智能体和角色卡，让对话有稳定的人设；可以接入不同供应商的模型，也可以在支持视觉的模型上真正读图。

它依然是一款 Android App，但更像一个贴身的 AI 控制台。轻一点，近一点，能长期用一点。

## 主要能力

- **多模态图片对话**：对话框旁边有独立图片上传入口。支持视觉的模型会收到真实图像输入，不支持的模型会明确提示无法读图。
- **计划模式**：快速、标准、深度、Codex 风格规划。先生成计划书，再由你选择执行、追加要求或取消。
- **智能体中心**：创建、查看、编辑、AI 补全、测试、绑定和解绑运行时智能体 Profile。
- **角色卡**：定义角色、语气、边界和行为。绑定后每一轮都会读取角色卡。
- **技能系统**：技能可以启用或停用，停用后不会消失；只有长按并二次确认才会删除。
- **动态工具**：运行时创建工具，下一轮对话即可让 AI 调用，不需要重新打包 APK。
- **记忆与压缩**：本地长期记忆和压缩记忆，结合句子评分与 gzip 归档，让长对话更可控。
- **模型控制台**：一个供应商可装备多个模型，拉取后可勾选，聊天顶部可快速切换。
- **搜索增强**：DuckDuckGo、DuckDuckGo Lite、Mojeek、Wikipedia、StackExchange 和浏览器 fallback。
- **附件对话**：图片、PDF、Word、文本文件和常见本地文档。
- **内嵌终端**：离线 arm64 运行时提供 Node.js/npm、Python/pip/uv、Git、SSH、shell 命令和 Agent 驱动代码执行。
- **主题工作台**：玻璃气泡、水玻璃、字体、字号、行距、聊天背景、侧边栏背景、透明度、模糊和可读性遮罩都能调。
- **120Hz 适配**：窗口会请求设备支持的高刷新模式，让滚动和动画更顺。
- **更安静的首页**：主页顶部保留留白，把注意力还给对话本身。

## 安装

直接从 [GitHub Releases](https://github.com/ly5201314gjx/LgClaw/releases/latest/download/LGClaw-Pro-debug.apk) 下载最新调试包。

> 这是 debug APK，适合测试和自用。正式公开分发建议使用自己的 keystore 重新签名 release 版本。

## 首次使用

1. 在手机上安装 APK。
2. 打开应用，进入设置里的模型供应商/模型控制台。
3. 填写 API Key 和 Base URL，拉取模型。
4. 勾选要装备的模型。
5. 回到对话页，在顶部快速切换模型。
6. 如果要读图，选择支持视觉的模型，比如 GPT-4o、GPT-4.1、GPT-5、Gemini、Claude、Qwen-VL、GLM-4V 等兼容模型。

## 项目截图

<table>
  <tr>
    <td width="33%" align="center">
      <img src="./docs/assets/site/demos/screenshot-chat.jpg" alt="LGClaw 聊天与读图" />
      <br />
      <strong>聊天与读图</strong>
      <br />
      <sub>图片、角色卡、计划模式和模型切换都放在对话流程里完成。</sub>
    </td>
    <td width="33%" align="center">
      <img src="./docs/assets/site/demos/screenshot-theme.jpg" alt="LGClaw 主题工作台" />
      <br />
      <strong>主题工作台</strong>
      <br />
      <sub>字体、气泡、背景和玻璃质感都能实时调节。</sub>
    </td>
    <td width="33%" align="center">
      <img src="./docs/assets/site/demos/screenshot-vision.jpg" alt="LGClaw 多模态消息" />
      <br />
      <strong>多模态消息</strong>
      <br />
      <sub>手机原图会被压缩成视觉输入，交给支持视觉的模型读取。</sub>
    </td>
  </tr>
</table>

## 从源码构建

环境要求：

- Android Studio
- JDK 17
- Android SDK
- `local.properties` 中配置本机 SDK 路径

构建、测试和 lint：

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --stacktrace
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 仓库结构

```text
LGClaw/
  app/src/main/java/com/lgclaw/
    agent/          智能体循环、上下文组装、计划调度
    agents/         智能体 Profile、角色卡、会话绑定
    memory/         长期记忆与压缩记忆
    providers/      OpenAI、Anthropic、Responses 等供应商兼容层
    skills/         技能加载、启用、匹配与运行
    tools/          Android、本地文件、搜索、网页、动态工具、终端工具
    ui/             Compose 聊天、设置、侧边栏、主题和面板
  app/src/main/assets/
    skills/         内置技能
    templates/      AGENT / USER / TOOLS / MEMORY / HEARTBEAT 模板
    terminal/       内嵌终端运行时资产
  docs/assets/      品牌、截图和文档资源
  LGClaw-Pro-debug.apk
```

## 隐私与安全

- 默认本地优先，应用数据保存在手机本地。
- 只有在你配置模型供应商、外部通道或显式调用工具时，相关内容才会发送到对应服务。
- API Key 由用户在应用内自行填写和保存。
- AI 只能使用运行时暴露的工具，以及你授予的 Android 权限。
- debug APK 方便测试，不建议直接作为正式生产版分发。

## 致谢

LGClaw 延续了 PalmClaw / OpenClaw 的移动端智能体方向，并在中文 UI、智能体、技能、工具、记忆、计划模式、角色卡、多模态和终端体验上做了大幅改造。

## 许可

见 [LICENSE](./LICENSE) 与 [LICENSE-COMMERCIAL.md](./LICENSE-COMMERCIAL.md)。
