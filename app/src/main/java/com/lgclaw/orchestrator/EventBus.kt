package com.lgclaw.orchestrator

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

/**
 * Internal event bus for the orchestrator. UI / Coordinator subscribe via
 * [events] (a [SharedFlow] with replay=0) to observe progress and completion
 * without coupling to the orchestrator's internal coroutine scope.
 *
 * The buffer is intentionally bounded: when 128 events accumulate without
 * being collected, the oldest event is dropped (DROP_OLDEST). The UI state
 * projector only cares about the latest snapshot anyway.
 */
class OrchestratorEventBus {
    private val _events = MutableSharedFlow<OrchestratorEvent>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<OrchestratorEvent> = _events.asSharedFlow()

    fun tryEmit(event: OrchestratorEvent): Boolean = _events.tryEmit(event)

    suspend fun emit(event: OrchestratorEvent) {
        _events.emit(event)
    }
}

@Serializable
sealed class OrchestratorEvent {
    abstract val runId: String

    @Serializable
    data class RunStarted(
        override val runId: String,
        val sessionId: String,
        val nodeCount: Int,
        val startedAt: Long
    ) : OrchestratorEvent()

    @Serializable
    data class NodeStarted(
        override val runId: String,
        val nodeId: String,
        val kind: NodeKind,
        val attempt: Int,
        val startedAt: Long
    ) : OrchestratorEvent()

    @Serializable
    data class NodeProgress(
        override val runId: String,
        val nodeId: String,
        val progress: Float,
        val message: String = ""
    ) : OrchestratorEvent()

    @Serializable
    data class NodeCompleted(
        override val runId: String,
        val nodeId: String,
        val outputSummary: String,
        val durationMs: Long
    ) : OrchestratorEvent()

    @Serializable
    data class NodeFailed(
        override val runId: String,
        val nodeId: String,
        val error: String,
        val attempt: Int,
        val terminal: Boolean
    ) : OrchestratorEvent()

    @Serializable
    data class GraphPaused(
        override val runId: String,
        val at: Long
    ) : OrchestratorEvent()

    @Serializable
    data class GraphResumed(
        override val runId: String,
        val at: Long
    ) : OrchestratorEvent()

    @Serializable
    data class GraphCancelled(
        override val runId: String,
        val at: Long
    ) : OrchestratorEvent()

    @Serializable
    data class GraphCompleted(
        override val runId: String,
        val finalOutput: String,
        val totalDurationMs: Long,
        val completedNodeCount: Int,
        val failedNodeCount: Int
    ) : OrchestratorEvent()

    @Serializable
    data class GraphFailed(
        override val runId: String,
        val reason: String,
        val at: Long
    ) : OrchestratorEvent()
}
