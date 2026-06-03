package com.lgclaw.orchestrator

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WorkerPool(
    initialConfig: PoolConfig = PoolConfig(),
    private val graph: TaskGraph,
    private val checkpoint: CheckpointStore,
    private val eventBus: OrchestratorEventBus,
    private val executor: NodeExecutor,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    data class PoolConfig(
        val min: Int = 2,
        val max: Int = 8,
        val queueCapacity: Int = 64
    )

    @Volatile
    private var config: PoolConfig = initialConfig
    private val actors = mutableListOf<WorkerActor>()
    private val dispatchQueue = Channel<TaskNode>(capacity = initialConfig.queueCapacity)
    private val resizeLock = Mutex()
    private val lifecycleScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var dispatcherJob: Job? = null

    @Volatile
    private var stopped: Boolean = false

    fun start() {
        if (dispatcherJob?.isActive == true) return
        val initial = config.min
        repeat(initial) { idx -> actors += spawnActor("worker-$idx") }
        dispatcherJob = lifecycleScope.launch { dispatcherLoop() }
    }

    fun currentSize(): Int = actors.size
    fun config(): PoolConfig = config

    fun submit(node: TaskNode): Boolean {
        if (stopped) return false
        val result = dispatchQueue.trySend(node)
        return result.isSuccess
    }

    fun cancelSubmission() {
        dispatchQueue.close()
        stopped = true
    }

    suspend fun resize(newConfig: PoolConfig) = resizeLock.withLock {
        val normalized = newConfig.copy(
            min = newConfig.min.coerceAtLeast(1),
            max = newConfig.max.coerceAtLeast(newConfig.min)
        )
        if (normalized == config) return@withLock
        val targetSize = normalized.max
        if (actors.size < targetSize) {
            val toAdd = targetSize - actors.size
            repeat(toAdd) { idx ->
                actors += spawnActor("worker-${actors.size}-$idx")
            }
        } else if (actors.size > targetSize) {
            val toRemove = actors.size - targetSize
            repeat(toRemove) {
                val last = actors.removeLastOrNull()
                last?.close()
            }
        }
        config = normalized
    }

    fun shutdown() {
        stopped = true
        dispatchQueue.close()
        actors.forEach { it.close() }
        lifecycleScope.cancel()
    }

    fun isStopped(): Boolean = stopped

    private fun spawnActor(label: String): WorkerActor {
        val actor = WorkerActor(
            id = label,
            graph = graph,
            checkpoint = checkpoint,
            eventBus = eventBus,
            executor = executor,
            dispatcherFactory = { dispatcher },
            onResult = {},
            onFailure = { _, _, _ -> },
            parentScope = lifecycleScope
        )
        actor.start()
        return actor
    }

    private suspend fun dispatcherLoop() = coroutineScope {
        while (isActive && !stopped) {
            val node = try {
                dispatchQueue.receive()
            } catch (e: kotlinx.coroutines.channels.ClosedReceiveChannelException) {
                break
            }
            val assigned = assignToIdleActor(node)
            if (!assigned) {
                delay(5)
                dispatchQueue.trySend(node)
            }
        }
    }

    private fun assignToIdleActor(node: TaskNode): Boolean {
        val actor = actors.firstOrNull { !it.isBusy }
        if (actor == null) return false
        return actor.submit(node)
    }
}