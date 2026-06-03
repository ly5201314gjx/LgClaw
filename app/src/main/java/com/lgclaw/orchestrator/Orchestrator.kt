package com.lgclaw.orchestrator

import com.lgclaw.agent.PlanningDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class Orchestrator(
    private val executor: NodeExecutor,
    private val checkpoint: CheckpointStore = CheckpointStore(),
    private val poolConfig: WorkerPool.PoolConfig = WorkerPool.PoolConfig(),
    private val adaptiveConfig: AdaptiveManager.AdaptiveConfig = AdaptiveManager.AdaptiveConfig(),
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) {
    data class OrchestratorResult(
        val runId: String,
        val finalOutput: String,
        val completedNodeCount: Int,
        val failedNodeCount: Int,
        val durationMs: Long,
        val status: RunState,
        val nodeSummaries: Map<String, String>
    )

    private val eventBus = OrchestratorEventBus()
    private val orchestratorScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val providerHint = AdaptiveManager.ProviderPriorityHint()

    val events: SharedFlow<OrchestratorEvent> = eventBus.events

    suspend fun run(plan: PlanningDispatcher.Plan, sessionId: String, originalTask: String = plan.steps.firstOrNull().orEmpty()): OrchestratorResult {
        val runId = "run-${UUID.randomUUID()}"
        val start = System.currentTimeMillis()
        val nodes = PlanStepMapper.map(plan, runId, PlanStepMapper.emptyOriginalTask(originalTask))
        val graph = TaskGraph(nodes)
        val pool = WorkerPool(
            initialConfig = poolConfig,
            graph = graph,
            checkpoint = checkpoint,
            eventBus = eventBus,
            executor = executor,
            dispatcher = dispatcher
        )
        val adaptive = AdaptiveManager(
            workerPool = pool,
            eventBus = eventBus,
            providerPriorityHint = providerHint,
            initialConfig = adaptiveConfig,
            dispatcher = dispatcher
        )
        pool.start()
        adaptive.start()
        eventBus.emit(
            OrchestratorEvent.RunStarted(
                runId = runId,
                sessionId = sessionId,
                nodeCount = nodes.size,
                startedAt = start
            )
        )
        val runRecord = PersistedRun(
            runId = runId,
            sessionId = sessionId,
            planMode = plan.mode,
            status = RunState.Running,
            createdAt = start,
            updatedAt = start,
            payloadJson = buildJsonObject {
                put("stepCount", plan.steps.size)
                put("originalTask", originalTask)
            }.toString()
        )
        checkpoint.saveRun(runRecord)
        val resultSignal = CompletableDeferred<OrchestratorResult>()
        val collectorJob = orchestratorScope.launch {
            var lastError: String? = null
            var completedCount = 0
            var failedCount = 0
            var finalOutput = ""
            val nodeSummaries = mutableMapOf<String, String>()
            eventBus.events.collect { event ->
                when (event) {
                    is OrchestratorEvent.NodeCompleted -> {
                        completedCount += 1
                        nodeSummaries[event.nodeId] = event.outputSummary
                    }
                    is OrchestratorEvent.NodeFailed -> {
                        if (event.terminal) failedCount += 1
                        lastError = event.error
                    }
                    is OrchestratorEvent.GraphCompleted -> {
                        finalOutput = event.finalOutput
                        val res = OrchestratorResult(
                            runId = runId,
                            finalOutput = finalOutput,
                            completedNodeCount = event.completedNodeCount,
                            failedNodeCount = event.failedNodeCount,
                            durationMs = event.totalDurationMs,
                            status = RunState.Completed,
                            nodeSummaries = nodeSummaries.toMap()
                        )
                        resultSignal.complete(res)
                    }
                    is OrchestratorEvent.GraphFailed -> {
                        val res = OrchestratorResult(
                            runId = runId,
                            finalOutput = lastError ?: event.reason,
                            completedNodeCount = completedCount,
                            failedNodeCount = failedCount,
                            durationMs = System.currentTimeMillis() - start,
                            status = RunState.Failed,
                            nodeSummaries = nodeSummaries.toMap()
                        )
                        resultSignal.complete(res)
                    }
                    else -> Unit
                }
            }
        }
        val dispatchJob = orchestratorScope.launch {
            try {
                dispatchLoop(graph, pool, runId)
            } finally {}
        }
        return try {
            val result = resultSignal.await()
            checkpoint.saveRun(runRecord.copy(status = result.status, updatedAt = System.currentTimeMillis()))
            result
        } finally {
            collectorJob.cancel()
            dispatchJob.cancel()
            adaptive.stop()
            pool.shutdown()
            orchestratorScope.cancel()
        }
    }

    private suspend fun dispatchLoop(graph: TaskGraph, pool: WorkerPool, runId: String) {
        val start = System.currentTimeMillis()
        var idleTicks = 0
        while (true) {
            if (graph.isCompleted()) {
                val finalOutput = buildFinalOutput(graph)
                eventBus.emit(
                    OrchestratorEvent.GraphCompleted(
                        runId = runId,
                        finalOutput = finalOutput,
                        totalDurationMs = System.currentTimeMillis() - start,
                        completedNodeCount = completedCount(graph),
                        failedNodeCount = failedCount(graph)
                    )
                )
                break
            }
            if (graph.hasFailedTerminal()) {
                eventBus.emit(
                    OrchestratorEvent.GraphFailed(
                        runId = runId,
                        reason = "graph has terminal failure",
                        at = System.currentTimeMillis()
                    )
                )
                break
            }
            val ready = graph.readyNodes()
            if (ready.isEmpty()) {
                if (graph.pendingOrRunningCount() == 0) {
                    val finalOutput = buildFinalOutput(graph)
                    eventBus.emit(
                        OrchestratorEvent.GraphCompleted(
                            runId = runId,
                            finalOutput = finalOutput,
                            totalDurationMs = System.currentTimeMillis() - start,
                            completedNodeCount = completedCount(graph),
                            failedNodeCount = failedCount(graph)
                        )
                    )
                    break
                }
                idleTicks += 1
                if (idleTicks > IDLE_LIMIT) {
                    eventBus.emit(
                        OrchestratorEvent.GraphFailed(
                            runId = runId,
                            reason = "idle for too long; possibly stuck",
                            at = System.currentTimeMillis()
                        )
                    )
                    break
                }
                kotlinx.coroutines.delay(20)
                continue
            }
            idleTicks = 0
            ready.forEach { node ->
                val submitted = pool.submit(node)
                if (submitted) {
                    graph.setState(node.id, NodeState.Ready)
                }
            }
            kotlinx.coroutines.delay(10)
        }
    }

    private fun buildFinalOutput(graph: TaskGraph): String {
        val outputs = graph.allOutputs()
        if (outputs.isEmpty()) return "Plan completed with no node output."
        return outputs.joinToString(separator = "\n\n") { (id, output) ->
            "$id: ${summarizeOutput(output)}"
        }
    }

    private fun summarizeOutput(output: JsonObject): String {
        val text = output["text"] as? JsonPrimitive
        val summary = output["summary"] as? JsonPrimitive
        return summary?.contentOrNull() ?: text?.contentOrNull() ?: output.toString()
    }

    private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else content

    private fun completedCount(graph: TaskGraph): Int =
        graph.statusSnapshot().count { it.state == NodeState.Completed }

    private fun failedCount(graph: TaskGraph): Int =
        graph.statusSnapshot().count { it.state == NodeState.Failed }

    fun pause() {}
    fun resume() {}
    fun cancel() { orchestratorScope.cancel() }

    companion object {
        private const val IDLE_LIMIT = 200
    }
}