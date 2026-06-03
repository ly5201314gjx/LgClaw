package com.lgclaw.orchestrator

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

class TaskGraph(
    initialNodes: List<TaskNode> = emptyList()
) {
    private val nodeLock = Mutex()
    private val nodes = ConcurrentHashMap<String, GraphEntry>()

    init {
        initialNodes.forEach { node ->
            require(node.id.isNotBlank()) { "TaskNode id must be non-blank" }
            nodes[node.id] = GraphEntry(node, NodeState.Pending, null, null, 0f, "")
        }
        requireNoCycles()
    }

    fun snapshotIds(): Set<String> = nodes.keys.toSet()
    fun size(): Int = nodes.size
    fun get(id: String): TaskNode? = nodes[id]?.node
    fun statusOf(id: String): NodeState? = nodes[id]?.state
    fun allStatuses(): Map<String, NodeState> = nodes.entries.associate { it.key to it.value.state }

    suspend fun readyNodes(): List<TaskNode> = nodeLock.withLock {
        val result = ArrayList<TaskNode>()
        for (entry in nodes.values) {
            if (entry.state != NodeState.Pending) continue
            val depsMet = entry.node.deps.all { dep ->
                val depState = nodes[dep]?.state
                depState == NodeState.Completed
            }
            if (depsMet) result += entry.node
        }
        result
    }

    suspend fun markRunning(id: String): NodeState? = nodeLock.withLock {
        val entry = nodes[id] ?: return null
        if (entry.state != NodeState.Pending && entry.state != NodeState.Ready) return null
        nodes[id] = entry.copy(state = NodeState.Running)
        NodeState.Running
    }

    suspend fun markCompleted(id: String, output: JsonObject, summary: String) = nodeLock.withLock {
        val entry = nodes[id] ?: return@withLock
        if (entry.state == NodeState.Cancelled) return@withLock
        nodes[id] = entry.copy(
            state = NodeState.Completed,
            output = output,
            outputSummary = summary,
            progress = 1f
        )
    }

    suspend fun markFailed(id: String, error: String, terminal: Boolean) = nodeLock.withLock {
        val entry = nodes[id] ?: return@withLock
        if (entry.state == NodeState.Cancelled) return@withLock
        val newState = if (terminal) NodeState.Failed else entry.state
        nodes[id] = entry.copy(state = newState, lastError = error)
    }

    suspend fun updateProgress(id: String, progress: Float, message: String) = nodeLock.withLock {
        val entry = nodes[id] ?: return@withLock
        if (entry.state != NodeState.Running) return@withLock
        val clamped = progress.coerceIn(0f, 1f)
        nodes[id] = entry.copy(progress = clamped, lastError = message)
    }

    suspend fun setState(id: String, state: NodeState) = nodeLock.withLock {
        val entry = nodes[id] ?: return@withLock
        if (entry.state == NodeState.Cancelled && state != NodeState.Cancelled) return@withLock
        nodes[id] = entry.copy(state = state)
    }

    suspend fun resetToPending(id: String) = nodeLock.withLock {
        val entry = nodes[id] ?: return@withLock
        if (entry.state == NodeState.Completed || entry.state == NodeState.Cancelled) return@withLock
        nodes[id] = entry.copy(state = NodeState.Pending, progress = 0f)
    }

    fun isCompleted(): Boolean = nodes.values.all { it.state == NodeState.Completed }
    fun hasFailedTerminal(): Boolean = nodes.values.any { it.state == NodeState.Failed }

    fun pendingOrRunningCount(): Int = nodes.values.count {
        it.state == NodeState.Pending || it.state == NodeState.Ready || it.state == NodeState.Running
    }

    fun outputOf(id: String): JsonObject? = nodes[id]?.output
    fun statusSnapshot(): List<NodeStatus> = nodes.values.map {
        NodeStatus(id = it.node.id, state = it.state, progress = it.progress, attempt = it.node.attempt, lastError = it.lastError)
    }
    fun allOutputs(): List<Pair<String, JsonObject>> = nodes.values.mapNotNull { entry ->
        entry.output?.let { entry.node.id to it }
    }

    private fun requireNoCycles() {
        val visited = HashSet<String>()
        val stack = HashSet<String>()
        fun dfs(id: String) {
            if (id in stack) throw IllegalStateException("TaskGraph has a cycle at $id")
            if (id in visited) return
            visited += id
            stack += id
            val deps = nodes[id]?.node?.deps ?: emptySet()
            deps.forEach { dfs(it) }
            stack -= id
        }
        nodes.keys.forEach { dfs(it) }
    }

    private data class GraphEntry(
        val node: TaskNode,
        val state: NodeState,
        val output: JsonObject?,
        val outputSummary: String?,
        val progress: Float,
        val lastError: String
    )
}