package com.lgclaw.engine

import android.content.Context
import android.util.Log
import com.lgclaw.orchestrator.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json

class EngineBootstrap private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "EngineBootstrap"
        @Volatile
        private var INSTANCE: EngineBootstrap? = null
        
        fun getInstance(context: Context): EngineBootstrap {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EngineBootstrap(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        fun reset() {
            synchronized(this) {
                INSTANCE?.shutdown()
                INSTANCE = null
            }
        }
    }
    
    private val supervisor = SupervisorJob()
    val mainScope: CoroutineScope = CoroutineScope(supervisor + Dispatchers.Main)
    val ioScope: CoroutineScope = CoroutineScope(supervisor + Dispatchers.IO)
    val computeScope: CoroutineScope = CoroutineScope(supervisor + Dispatchers.Default)
    
    lateinit var orchestratorCore: Orchestrator
        private set
    lateinit var eventBusCore: OrchestratorEventBus
        private set
    lateinit var checkpointStoreCore: CheckpointStore
        private set
    
    val isInitialized: Boolean
        get() = ::orchestratorCore.isInitialized
    
    fun initialize(executor: NodeExecutor? = null) {
        if (isInitialized) {
            Log.w(TAG, "Engine already initialized")
            return
        }
        
        Log.i(TAG, "Initializing engine...")
        
        checkpointStoreCore = CheckpointStore()
        eventBusCore = OrchestratorEventBus()
        orchestratorCore = Orchestrator(
            executor = executor ?: DefaultNodeExecutorStub(),
            checkpoint = checkpointStoreCore
        )
        
        Log.i(TAG, "Engine initialized successfully")
    }
    
    fun shutdown() {
        if (!isInitialized) return
        Log.i(TAG, "Shutting down engine...")
        
        supervisor.cancel()
        mainScope.cancel()
        ioScope.cancel()
        computeScope.cancel()
        
        Log.i(TAG, "Engine shutdown complete")
    }
    
    fun getOrchestrator(): Orchestrator = orchestratorCore
    fun getEventBus(): OrchestratorEventBus = eventBusCore
    fun getCheckpointStore(): CheckpointStore = checkpointStoreCore
}

class DefaultNodeExecutorStub : NodeExecutor {
    private val json = Json { ignoreUnknownKeys = true }
    
    override suspend fun execute(
        node: TaskNode,
        onProgress: suspend (Float, String) -> Unit
    ): NodeResult.Success {
        val output = json.parseToJsonElement("""{"status": "stub", "nodeId": "${node.id}"}""")
        return NodeResult.Success(
            output = output as? kotlinx.serialization.json.JsonObject 
                ?: kotlinx.serialization.json.JsonObject(emptyMap()),
            summary = "Stub execution",
            durationMs = 0L
        )
    }
}