package com.lgclaw.orchestrator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A single execution slot in the [WorkerPool]. Each [WorkerActor] owns a
 * coroutine and processes nodes from a bounded mailbox channel.
 *
 * The actor pattern is implemented with a [Channel]-driven loop rather than
 * `kotlinx.coroutines.actor` so we can keep explicit lifecycle and graceful
 * shutdown semantics.
 */
class WorkerActor(
    val id: String,
    private val graph: TaskGraph,
    private val checkpoint: CheckpointStore,
    private val eventBus: OrchestratorEventBus,
    private val executor: NodeExecutor,
    private val dispatcherFactory: () -> CoroutineDispatcher,
    private val onResult: suspend (NodeResult) -> Unit,
    private val onFailure: suspend (nodeId: String, error: String, terminal: Boolean) -> Unit,
    parentScope: CoroutineScope
) {
    @Volatile
    private var currentNode: TaskNode? = null
    private val actorScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + dispatcherFactory()
    )
    private val mailbox = Channel<Mail>(capacity = Channel.BUFFERED)

    val isBusy: Boolean
        get() = currentNode != null

    val currentNodeId: String?
        get() = currentNode?.id

    fun start() {
        actorScope.launch {
            for (mail in mailbox) {
                try {
                    handle(mail)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Last-resort safety net: the executor should never leak, but if it does we
                    // must not kill the actor loop.
                    eventBus.emit(
                        OrchestratorEvent.NodeFailed(
                            runId = mail.node.id,
                            nodeId = mail.node.id,
                            error = "actor loop swallowed ${e.javaClass.simpleName}: ${e.message}",
                            attempt = mail.node.attempt,
                            terminal = true
                        )
                    )
                }
            }
        }
    }

    fun submit(node: TaskNode): Boolean = mailbox.trySend(Mail(node)).isSuccess

    fun close() {
        mailbox.close()
        actorScope.cancel()
    }

    private suspend fun handle(mail: Mail) {
        val node = mail.node
        currentNode = node
        try {
            checkpoint.saveNode(node, NodeState.Running, System.currentTimeMillis())
            eventBus.emit(
                OrchestratorEvent.NodeStarted(
                    runId = node.id,
                    nodeId = node.id,
                    kind = node.kind,
                    attempt = node.attempt,
                    startedAt = System.currentTimeMillis()
                )
            )
            val result = executeWithTimeout(node)
            when (result) {
                is NodeResult.Success -> {
                    graph.markCompleted(node.id, result.output, result.summary)
                    checkpoint.saveNode(
                        node,
                        NodeState.Completed,
                        System.currentTimeMillis(),
                        result.output,
                        result.summary
                    )
                    eventBus.emit(
                        OrchestratorEvent.NodeCompleted(
                            runId = node.id,
                            nodeId = node.id,
                            outputSummary = result.summary,
                            durationMs = result.durationMs
                        )
                    )
                    onResult(result)
                }
                is NodeResult.Failure -> {
                    val willRetry = node.attempt + 1 < node.maxAttempts && !result.terminal
                    val nextState = if (willRetry) NodeState.Pending else NodeState.Failed
                    graph.markFailed(node.id, result.error, terminal = !willRetry)
                    if (willRetry) {
                        graph.resetToPending(node.id)
                    }
                    checkpoint.saveNode(
                        node.copy(attempt = node.attempt + 1),
                        nextState,
                        System.currentTimeMillis(),
                        lastError = result.error
                    )
                    eventBus.emit(
                        OrchestratorEvent.NodeFailed(
                            runId = node.id,
                            nodeId = node.id,
                            error = result.error,
                            attempt = node.attempt,
                            terminal = !willRetry
                        )
                    )
                    onFailure(node.id, result.error, !willRetry)
                }
            }
        } catch (e: CancellationException) {
            graph.setState(node.id, NodeState.Cancelled)
            checkpoint.saveNode(node, NodeState.Cancelled, System.currentTimeMillis(), lastError = "cancelled")
            eventBus.emit(
                OrchestratorEvent.NodeFailed(
                    runId = node.id,
                    nodeId = node.id,
                    error = "cancelled",
                    attempt = node.attempt,
                    terminal = true
                )
            )
            throw e
        } finally {
            currentNode = null
        }
    }

    private suspend fun executeWithTimeout(node: TaskNode): NodeResult {
        val timeoutMs = node.timeoutMs.coerceAtLeast(1_000L)
        val started = System.currentTimeMillis()
        return try {
            val success = withTimeout(timeoutMs) {
                executor.execute(node) { progress, message ->
                    val clamped = progress.coerceIn(0f, 1f)
                    graph.updateProgress(node.id, clamped, message)
                    eventBus.emit(
                        OrchestratorEvent.NodeProgress(
                            runId = node.id,
                            nodeId = node.id,
                            progress = clamped,
                            message = message
                        )
                    )
                }
            }
            NodeResult.Success(
                output = success.output,
                summary = success.summary,
                durationMs = System.currentTimeMillis() - started
            )
        } catch (e: TimeoutCancellationException) {
            NodeResult.Failure(
                error = "timeout after ${timeoutMs}ms",
                terminal = true,
                durationMs = System.currentTimeMillis() - started
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            NodeResult.Failure(
                error = e.message ?: e.javaClass.simpleName,
                terminal = false,
                durationMs = System.currentTimeMillis() - started
            )
        }
    }

    private data class Mail(val node: TaskNode)
}

sealed class NodeResult {
    abstract val durationMs: Long

    data class Success(
        val output: JsonObject,
        val summary: String,
        override val durationMs: Long
    ) : NodeResult()

    data class Failure(
        val error: String,
        val terminal: Boolean,
        override val durationMs: Long
    ) : NodeResult()
}

interface NodeExecutor {
    suspend fun execute(
        node: TaskNode,
        onProgress: suspend (Float, String) -> Unit
    ): NodeResult.Success
}

fun emptyOutput(): JsonObject = buildJsonObject { }
fun primitive(value: String): JsonPrimitive = JsonPrimitive(value)
fun primitive(value: Number): JsonPrimitive = JsonPrimitive(value)
