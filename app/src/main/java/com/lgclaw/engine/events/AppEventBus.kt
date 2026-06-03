package com.lgclaw.engine.events

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.lgclaw.orchestrator.OrchestratorEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * UI event bus for engine events
 */
class AppEventBus {
    
    private val _uiEvents = MutableSharedFlow<UiEvent>(replay = 1, extraBufferCapacity = 64)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()
    
    private val _progressEvents = MutableSharedFlow<ProgressEvent>(replay = 1, extraBufferCapacity = 128)
    val progressEvents: SharedFlow<ProgressEvent> = _progressEvents.asSharedFlow()
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    suspend fun emitUiEvent(event: UiEvent) {
        _uiEvents.emit(event)
    }
    
    suspend fun emitProgress(event: ProgressEvent) {
        _progressEvents.emit(event)
    }
    
    fun subscribeUiEvents(owner: LifecycleOwner, callback: (UiEvent) -> Unit) {
        owner.lifecycleScope.launch {
            uiEvents.collect { event ->
                mainHandler.post { callback(event) }
            }
        }
    }
    
    fun subscribeProgress(owner: LifecycleOwner, callback: (ProgressEvent) -> Unit) {
        owner.lifecycleScope.launch {
            progressEvents.collect { event ->
                mainHandler.post { callback(event) }
            }
        }
    }
}

sealed class UiEvent {
    abstract val runId: String
    
    data class RunStarted(override val runId: String, val sessionId: String, val nodeCount: Int) : UiEvent()
    data class NodeCompleted(override val runId: String, val nodeId: String, val summary: String) : UiEvent()
    data class NodeFailed(override val runId: String, val nodeId: String, val error: String, val terminal: Boolean) : UiEvent()
    data class GraphCompleted(override val runId: String, val finalOutput: String, val completedCount: Int, val failedCount: Int) : UiEvent()
    data class GraphFailed(override val runId: String, val reason: String) : UiEvent()
}

data class ProgressEvent(
    val runId: String,
    val nodeId: String,
    val progress: Float,
    val message: String = ""
)