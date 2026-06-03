package com.lgclaw.orchestrator

import com.lgclaw.agent.PlanningDispatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts a [PlanningDispatcher.Plan] into a list of [TaskNode]s with explicit
 * happens-before dependencies.
 *
 * Default rules:
 *  - Steps are numbered sequentially and chained with happens-before edges.
 *  - Steps whose label contains a parallel-marker ("and", "同时", "parallel",
 *    "并") break the chain: all parallel siblings become free (they only
 *    depend on the most recent non-parallel step or the entry node).
 *  - A trailing [NodeKind.Aggregate] node joins the final outputs of the
 *    last batch.
 */
object PlanStepMapper {
    private val PARALLEL_MARKERS = setOf("and", "同时", "parallel", "并", "并且", "&")

    fun map(plan: PlanningDispatcher.Plan, runId: String, originalTask: String): List<TaskNode> {
        val steps = plan.steps
        if (steps.isEmpty()) {
            // Even an empty plan should produce a single aggregate node so the
            // orchestrator can run, report, and complete deterministically.
            return listOf(aggregateNode(runId, originalTask, plan))
        }
        val nodes = mutableListOf<TaskNode>()
        var previous: String? = null
        var lastAggregator: String? = null
        steps.forEachIndexed { index, step ->
            val trimmed = step.trim()
            if (trimmed.isEmpty()) return@forEachIndexed
            val isParallel = PARALLEL_MARKERS.any { marker -> trimmed.lowercase().contains(marker.lowercase()) }
            val deps = if (isParallel) {
                val base = lastAggregator ?: previous
                if (base == null) emptySet() else setOf(base)
            } else {
                if (previous == null) emptySet() else setOf(previous)
            }
            val nodeId = "${runId}-step-${index + 1}"
            val node = TaskNode(
                id = nodeId,
                kind = NodeKind.ToolCall,
                deps = deps,
                label = trimmed,
                input = buildJsonObject {
                    put("stepIndex", index + 1)
                    put("stepText", trimmed)
                    put("originalTask", originalTask)
                    put("planMode", plan.mode)
                },
                timeoutMs = 30_000L
            )
            nodes += node
            previous = nodeId
            if (!isParallel) lastAggregator = nodeId
        }
        val aggregatorDeps = if (nodes.isEmpty()) emptySet() else setOf(nodes.last().id)
        nodes += TaskNode(
            id = "${runId}-aggregate",
            kind = NodeKind.Aggregate,
            deps = aggregatorDeps,
            label = "aggregate",
            input = buildJsonObject {
                put("stepCount", nodes.size)
                put("originalTask", originalTask)
            },
            timeoutMs = 10_000L
        )
        return nodes
    }

    private fun aggregateNode(runId: String, originalTask: String, plan: PlanningDispatcher.Plan): TaskNode {
        return TaskNode(
            id = "${runId}-aggregate",
            kind = NodeKind.Aggregate,
            label = "aggregate",
            input = buildJsonObject {
                put("stepCount", 0)
                put("originalTask", originalTask)
                put("planMode", plan.mode)
                put("empty", true)
            }
        )
    }

    fun emptyOriginalTask(task: String): String = task.trim().ifBlank { "Untitled plan" }

    fun payloadToString(node: TaskNode): String = node.input.toString()

    fun stepsFromArray(raw: JsonArray?): List<String> {
        if (raw == null) return emptyList()
        return raw.mapNotNull { element ->
            (element as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }
    }
}
