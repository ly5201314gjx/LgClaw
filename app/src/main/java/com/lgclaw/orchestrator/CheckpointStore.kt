package com.lgclaw.orchestrator

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

class CheckpointStore(
    private val saver: Saver = InMemorySaver()
) {
    interface Saver {
        suspend fun saveRun(run: PersistedRun)
        suspend fun saveNode(node: PersistedNode)
        suspend fun loadRun(runId: String): PersistedRun?
        suspend fun loadNodes(runId: String): List<PersistedNode>
        suspend fun latestRunForSession(sessionId: String): PersistedRun?
        suspend fun prune(olderThanMs: Long): Int
        suspend fun markStaleAsInterrupted(updatedBefore: Long): Int
    }

    private val memory: MutableMap<String, PersistedRun> = ConcurrentHashMap()
    private val nodeMemory: MutableMap<String, MutableList<PersistedNode>> = ConcurrentHashMap()
    private val writeLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun setSaver(saver: Saver) {}

    suspend fun saveRun(run: PersistedRun) {
        writeLock.withLock {
            memory[run.runId] = run
            saver.saveRun(run)
        }
    }

    suspend fun saveNode(node: TaskNode, state: NodeState, updatedAt: Long, output: JsonObject? = null, summary: String = "", lastError: String = "") {
        writeLock.withLock {
            val persisted = PersistedNode(
                runId = node.id,
                nodeId = node.id,
                kind = node.kind.name,
                state = state.name,
                attempt = node.attempt,
                deps = node.deps,
                input = node.input.toString(),
                output = output?.toString().orEmpty(),
                summary = summary,
                lastError = lastError,
                updatedAt = updatedAt
            )
            val list = nodeMemory.getOrPut(node.id) { mutableListOf() }
            val existingIdx = list.indexOfFirst { it.nodeId == node.id }
            if (existingIdx >= 0) list[existingIdx] = persisted else list += persisted
            saver.saveNode(persisted)
        }
    }

    suspend fun loadRun(runId: String): PersistedRun? {
        return memory[runId] ?: saver.loadRun(runId)
    }

    suspend fun loadNodes(runId: String): List<PersistedNode> {
        return nodeMemory[runId]?.toList() ?: saver.loadNodes(runId)
    }

    suspend fun latestRunForSession(sessionId: String): PersistedRun? {
        return memory.values.filter { it.sessionId == sessionId }.maxByOrNull { it.updatedAt }
            ?: saver.latestRunForSession(sessionId)
    }

    suspend fun prune(olderThanMs: Long): Int = writeLock.withLock {
        val cutoff = System.currentTimeMillis() - olderThanMs
        val toRemove = memory.entries.filter { it.value.updatedAt < cutoff }.map { it.key }
        toRemove.forEach { memory.remove(it) }
        toRemove.forEach { nodeMemory.remove(it) }
        saver.prune(olderThanMs) + toRemove.size
    }

    suspend fun markStaleAsInterrupted(updatedBefore: Long): Int = writeLock.withLock {
        var count = 0
        memory.replaceAll { _, run ->
            if (run.status == RunState.Running && run.updatedAt < updatedBefore) {
                count += 1
                run.copy(status = RunState.Interrupted, updatedAt = System.currentTimeMillis())
            } else run
        }
        saver.markStaleAsInterrupted(updatedBefore) + count
    }

    fun parseInput(jsonText: String): JsonObject {
        if (jsonText.isBlank()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(jsonText) as? JsonObject ?: JsonObject(emptyMap()) }
            .getOrDefault(JsonObject(emptyMap()))
    }
}

data class PersistedRun(
    val runId: String,
    val sessionId: String,
    val planMode: String,
    val status: RunState,
    val createdAt: Long,
    val updatedAt: Long,
    val payloadJson: String
)

data class PersistedNode(
    val runId: String,
    val nodeId: String,
    val kind: String,
    val state: String,
    val attempt: Int,
    val deps: Set<String>,
    val input: String,
    val output: String,
    val summary: String,
    val lastError: String,
    val updatedAt: Long
)

enum class RunState {
    Pending,
    Running,
    Paused,
    Completed,
    Failed,
    Cancelled,
    Interrupted
}

class InMemorySaver : CheckpointStore.Saver {
    private val runs = ConcurrentHashMap<String, PersistedRun>()
    private val nodes = ConcurrentHashMap<String, MutableList<PersistedNode>>()

    override suspend fun saveRun(run: PersistedRun) {
        runs[run.runId] = run
    }

    override suspend fun saveNode(node: PersistedNode) {
        val list = nodes.getOrPut(node.runId) { mutableListOf() }
        val idx = list.indexOfFirst { it.nodeId == node.nodeId }
        if (idx >= 0) list[idx] = node else list += node
    }

    override suspend fun loadRun(runId: String): PersistedRun? = runs[runId]

    override suspend fun loadNodes(runId: String): List<PersistedNode> = nodes[runId]?.toList().orEmpty()

    override suspend fun latestRunForSession(sessionId: String): PersistedRun? =
        runs.values.filter { it.sessionId == sessionId }.maxByOrNull { it.updatedAt }

    override suspend fun prune(olderThanMs: Long): Int {
        val cutoff = System.currentTimeMillis() - olderThanMs
        val keys = runs.entries.filter { it.value.updatedAt < cutoff }.map { it.key }
        keys.forEach {
            runs.remove(it)
            nodes.remove(it)
        }
        return keys.size
    }

    override suspend fun markStaleAsInterrupted(updatedBefore: Long): Int {
        var count = 0
        runs.replaceAll { _, run ->
            if (run.status == RunState.Running && run.updatedAt < updatedBefore) {
                count += 1
                run.copy(status = RunState.Interrupted, updatedAt = System.currentTimeMillis())
            } else run
        }
        return count
    }
}