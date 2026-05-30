package com.lgclaw.agent

import com.lgclaw.providers.ChatMessage
import com.lgclaw.providers.LlmProvider
import java.util.Locale

object PlanningDispatcher {
    data class Request(
        val userTask: String,
        val mode: String,
        val additions: String = "",
        val recentContext: String = ""
    )

    data class Plan(
        val mode: String,
        val text: String,
        val executionPrompt: String,
        val steps: List<String>,
        val riskNotes: List<String>
    )

    suspend fun createPlan(provider: LlmProvider, request: Request): Plan {
        val local = localDraft(request)
        val response = runCatching {
            provider.chat(
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = buildSystemPrompt(request.mode)
                    ),
                    ChatMessage(
                        role = "user",
                        content = buildUserPrompt(request)
                    )
                ),
                toolsSpec = emptyList()
            )
        }.getOrNull()
        val text = response?.assistant?.content?.trim().orEmpty().ifBlank { local.text }
        val merged = normalizePlanText(text, request.mode, local)
        return merged.copy(executionPrompt = buildExecutionPrompt(request, merged))
    }

    fun localDraft(request: Request): Plan {
        val modeLabel = modeLabel(request.mode)
        val task = request.userTask.trim()
        val additions = request.additions.trim()
        val steps = inferSteps(task, request.mode, additions)
        val risks = inferRiskNotes(task)
        val text = buildString {
            appendLine("# $modeLabel")
            appendLine()
            appendLine("## 目标")
            appendLine(task.ifBlank { "完成用户当前任务。" })
            if (additions.isNotBlank()) {
                appendLine()
                appendLine("## 补充要求")
                appendLine(additions)
            }
            appendLine()
            appendLine("## 执行计划")
            steps.forEachIndexed { index, step -> appendLine("${index + 1}. $step") }
            appendLine()
            appendLine("## 调度策略")
            appendLine(strategyForMode(request.mode))
            appendLine()
            appendLine("## 风险与确认点")
            risks.forEach { appendLine("- $it") }
        }.trim()
        return Plan(request.mode, text, "", steps, risks)
    }

    private fun buildSystemPrompt(mode: String): String = """
        你是 LGClaw 的计划模式调度器，参考 Codex 的工作方式：先理解目标，拆解任务，识别工具/技能/数据风险，再等待用户确认后执行。
        你现在只允许产出计划，禁止执行任务，禁止声称已经调用工具、写入文件、联网或修改系统。
        输出必须为中文，结构固定为：目标、执行计划、调度策略、风险与确认点、执行后验收。
        计划要具体、可执行、不要空泛；复杂任务要说明先后顺序、可并行项、失败 fallback。
        当前模式：${modeLabel(mode)}。
    """.trimIndent()

    private fun buildUserPrompt(request: Request): String = buildString {
        appendLine("用户任务：")
        appendLine(request.userTask.trim())
        if (request.additions.isNotBlank()) {
            appendLine()
            appendLine("用户追加要求：")
            appendLine(request.additions.trim())
        }
        if (request.recentContext.isNotBlank()) {
            appendLine()
            appendLine("近期上下文摘要：")
            appendLine(request.recentContext.trim().take(1800))
        }
    }

    private fun normalizePlanText(text: String, mode: String, fallback: Plan): Plan {
        val clean = text.trim().ifBlank { fallback.text }
        val numbered = Regex("(?m)^\\s*(?:\\d+[.、)]|[-*])\\s+(.+)$")
            .findAll(clean)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .take(12)
            .toList()
        val steps = numbered.ifEmpty { fallback.steps }
        val riskNotes = clean.lineSequence()
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.contains("风险") || it.contains("确认") || it.contains("注意") }
            .take(6)
            .toList()
            .ifEmpty { fallback.riskNotes }
        return Plan(mode, clean, "", steps, riskNotes)
    }

    private fun buildExecutionPrompt(request: Request, plan: Plan): String = buildString {
        appendLine("用户已确认执行以下计划。请按照计划执行，不要重新停留在规划阶段。")
        appendLine()
        appendLine("原始任务：")
        appendLine(request.userTask.trim())
        if (request.additions.isNotBlank()) {
            appendLine()
            appendLine("追加要求：")
            appendLine(request.additions.trim())
        }
        appendLine()
        appendLine("已确认计划：")
        appendLine(plan.text.trim())
        appendLine()
        appendLine("执行规则：按步骤推进；需要工具时调用可用工具；遇到失败要使用 fallback；最终汇报完成情况和验证结果。")
    }

    private fun inferSteps(task: String, mode: String, additions: String): List<String> {
        val lower = task.lowercase(Locale.US)
        val base = mutableListOf("确认目标、约束和当前上下文", "拆分可执行步骤并判断需要的技能或工具")
        if (listOf("代码", "开发", "修复", "bug", "编译", "apk", "实现").any { lower.contains(it) }) {
            base += listOf("检查相关代码入口和数据流", "小步修改并保持现有功能兼容", "运行编译、测试或可用性验证")
        } else if (listOf("搜索", "网页", "资料", "调研").any { lower.contains(it) }) {
            base += listOf("选择合适搜索源并交叉验证结果", "提取关键证据并整理结论")
        } else {
            base += listOf("执行主要任务", "检查结果是否符合用户要求")
        }
        if (additions.isNotBlank()) base += "合并用户追加要求并重新校验计划"
        if (mode.equals("deep", true) || mode.equals("codex", true)) {
            base += listOf("记录风险、回滚点和验收标准", "完成后给出简洁结果和下一步建议")
        }
        return base.distinct().take(8)
    }

    private fun inferRiskNotes(task: String): List<String> {
        val notes = mutableListOf("执行前需要用户确认计划，未确认不执行。")
        if (task.contains("删除") || task.contains("清空")) notes += "涉及删除或覆盖时需要二次确认。"
        if (task.contains("联网") || task.contains("搜索")) notes += "联网结果可能受搜索源可用性影响，需要 fallback。"
        if (task.contains("代码") || task.contains("开发") || task.contains("apk")) notes += "代码修改后必须编译或测试验证。"
        return notes
    }

    private fun strategyForMode(mode: String): String = when (mode.lowercase(Locale.US)) {
        "quick" -> "快速计划：少量步骤，优先直接完成低风险任务。"
        "deep" -> "深度计划：先分析依赖、风险和验收，再执行关键步骤。"
        "codex" -> "Codex 调度：探索上下文 -> 制定步骤 -> 用户确认 -> 执行 -> 验证 -> 汇报。"
        else -> "标准计划：先给出清晰步骤，用户确认后再执行。"
    }

    private fun modeLabel(mode: String): String = when (mode.lowercase(Locale.US)) {
        "quick" -> "快速计划"
        "deep" -> "深度计划"
        "codex" -> "Codex 调度计划"
        else -> "标准计划"
    }
}
