package com.lgclaw.orchestrator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Adaptive controller that watches the orchestrator's [OrchestratorEventBus]
 * and the host device's memory pressure, then nudges [WorkerPool] concurrency,
 * per-node timeouts, and provider fallback preferences.
 *
 * Strategy summary (see [adjustNow] for the actual table):
 *  - LLM node timeout scales with `ln(1 + itemSize)`.
 *  - Tool/Terminal node timeout scales linearly with attempt.
 *  - 3 consecutive timeouts/429s on any LLM-kind node -> single-concurrency +
 *    promote a fallback provider to first priority.
 *  - Memory pressure >= 70% -> reduce parallelism by 2; >= 85% -> by 4.
 *  - Memory pressure >= 95% -> cancel pending queue submissions (handled at
 *    the pool level via [WorkerPool.cancelSubmission]).
 */
class AdaptiveManager(
    private val workerPool: WorkerPool,
    private val eventBus: OrchestratorEventBus,
    private val providerPriorityHint: ProviderPriorityHint,
    initialConfig: AdaptiveConfig = AdaptiveConfig(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    data class AdaptiveConfig(
        val baseLlmTimeoutMs: Long = 30_000L,
        val baseToolTimeoutMs: Long = 20_000L,
        val baseTerminalTimeoutMs: Long = 60_000L,
        val sampleIntervalMs: Long = 5_000L,
        val memorySampleProvider: () -> MemorySample = { MemorySample() }
    )

    data class MemorySample(
        val usedRatio: Float = 0f,
        val trimLevel: Int = 0
    )

    data class ProviderPriorityHint(
        var fallbackEnabled: Boolean = false,
        var lastEscalationAt: Long = 0L
    )

    private val config = AtomicReference(initialConfig)
    private val consecutiveFailures = AtomicLong(0L)
    private val avgLatencyMs = AtomicLong(0L)
    private val sampleCount = AtomicLong(0L)
    private val ownScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var samplerJob: Job? = null
    private val decisionLock = Mutex()

    @Volatile
    private var currentPoolSize: Int = workerPool.currentSize()

    fun start() {
        if (samplerJob?.isActive == true) return
        samplerJob = ownScope.launch {
            while (isActive) {
                sample()
                delay(config.get().sampleIntervalMs)
            }
        }
    }

    fun stop() {
        samplerJob?.cancel()
        samplerJob = null
        ownScope.cancel()
    }

    fun onTrim(level: Int) {
        val current = config.get()
        val newSample = current.memorySampleProvider().copy(trimLevel = level)
        config.set(current.copy(memorySampleProvider = { newSample }))
    }

    fun recordOutcome(node: TaskNode, result: NodeResult) {
        sampleCount.incrementAndGet()
        val alpha = 0.3
        val observed = result.durationMs.toDouble()
        val prev = avgLatencyMs.get().toDouble()
        val next = if (prev == 0.0) observed else prev * (1 - alpha) + observed * alpha
        avgLatencyMs.set(next.toLong())
        if (result is NodeResult.Failure) {
            consecutiveFailures.incrementAndGet()
        } else {
            consecutiveFailures.set(0)
        }
    }

    /**
     * Compute the dynamic timeout for a given node. Pure function so it can be
     * tested without launching the sampler.
     */
    fun computeTimeoutMs(node: TaskNode, configOverride: AdaptiveConfig = config.get()): Long {
        val base = when (node.kind) {
            NodeKind.LlmCall -> configOverride.baseLlmTimeoutMs
            NodeKind.ToolCall -> configOverride.baseToolTimeoutMs
            NodeKind.SkillCall -> configOverride.baseToolTimeoutMs
            NodeKind.TerminalCall -> configOverride.baseTerminalTimeoutMs
            NodeKind.Aggregate -> configOverride.baseLlmTimeoutMs
        }
        return when (node.kind) {
            NodeKind.LlmCall, NodeKind.Aggregate -> {
                val payload = node.input.toString().length
                val scale = 1.0 + kotlin.math.ln(1.0 + payload.toDouble() / 1024.0)
                (base * scale).toLong().coerceAtLeast(base)
            }
            NodeKind.ToolCall, NodeKind.SkillCall, NodeKind.TerminalCall -> {
                val attemptScale = 1.0 + node.attempt.toDouble()
                (base * attemptScale).toLong()
            }
        }
    }

    suspend fun adjustNow() = decisionLock.withLock {
        val sample = config.get().memorySampleProvider()
        val failures = consecutiveFailures.get()
        val targetSize = computeTargetSize(sample, currentPoolSize)
        if (targetSize != workerPool.currentSize()) {
            workerPool.resize(WorkerPool.PoolConfig(min = 1, max = targetSize))
            currentPoolSize = targetSize
        }
        if (failures >= FAILURE_ESCALATION_THRESHOLD && !providerPriorityHint.fallbackEnabled) {
            providerPriorityHint.fallbackEnabled = true
            providerPriorityHint.lastEscalationAt = System.currentTimeMillis()
            consecutiveFailures.set(0)
        } else if (failures == 0L && providerPriorityHint.fallbackEnabled &&
            System.currentTimeMillis() - providerPriorityHint.lastEscalationAt > COOLDOWN_MS
        ) {
            providerPriorityHint.fallbackEnabled = false
        }
        if (sample.usedRatio >= CRITICAL_RATIO) {
            workerPool.cancelSubmission()
        }
    }

    private fun computeTargetSize(sample: MemorySample, current: Int): Int {
        val baseMax = (current + 2).coerceAtLeast(2)
        return when {
            sample.usedRatio >= 0.95f -> 1
            sample.usedRatio >= 0.85f -> (baseMax - 4).coerceAtLeast(1)
            sample.usedRatio >= 0.70f -> (baseMax - 2).coerceAtLeast(1)
            sample.trimLevel >= TRIM_LEVEL_RUNNING_CRITICAL -> 1
            sample.trimLevel >= TRIM_LEVEL_RUNNING_LOW -> (baseMax - 1).coerceAtLeast(1)
            else -> baseMax
        }
    }

    private suspend fun sample() {
        try {
            adjustNow()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Adaptive sampling must never crash the orchestrator.
        }
    }

    companion object {
        const val FAILURE_ESCALATION_THRESHOLD: Long = 3L
        const val COOLDOWN_MS: Long = 30_000L
        const val CRITICAL_RATIO: Float = 0.95f
        const val TRIM_LEVEL_RUNNING_LOW: Int = 10 // TRIM_MEMORY_RUNNING_LOW
        const val TRIM_LEVEL_RUNNING_CRITICAL: Int = 15 // TRIM_MEMORY_RUNNING_CRITICAL
    }
}
