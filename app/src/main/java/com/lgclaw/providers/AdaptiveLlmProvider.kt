package com.lgclaw.providers

import com.lgclaw.config.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient

internal class AdaptiveLlmProvider(
    private val profile: ProviderProfile,
    private val config: AppConfig,
    private val client: OkHttpClient,
    private val resolutionStore: ProviderResolutionStore? = null
) : LlmProvider {

    override suspend fun chat(messages: List<ChatMessage>, toolsSpec: List<ToolSpec>): LlmResponse {
        val targets = plannedTargets()
        var lastFailure: Throwable? = null
        for ((index, target) in targets.withIndex()) {
            try {
                val response = createDelegate(target).chat(messages, toolsSpec)
                rememberSuccessfulTarget(target)
                return response
            } catch (t: Throwable) {
                lastFailure = t
                forgetRememberedTargetIfNeeded(t)
                if (!shouldTryNextCandidate(t, index, targets.lastIndex)) {
                    throw recoverableFailure(t) ?: t
                }
            }
        }
        throw recoverableFailure(lastFailure) ?: IOException("${profile.title} request failed.")
    }

    override fun chatStream(messages: List<ChatMessage>, toolsSpec: List<ToolSpec>): Flow<LlmStreamEvent> {
        val targets = plannedTargets()
        if (targets.isEmpty()) {
            throw IllegalStateException("No usable endpoint target resolved.")
        }
        return flow {
            var lastFailure: Throwable? = null
            candidates@ for ((index, target) in targets.withIndex()) {
                var emittedContent = false
                var shouldTryNext = false
                var failureForCandidate: Throwable? = null
                var completed = false
                createDelegate(target).chatStream(messages, toolsSpec)
                    .catch { error ->
                        failureForCandidate = error
                    }
                    .collect { event ->
                        when (event) {
                            is LlmStreamEvent.DeltaText -> {
                                emittedContent = true
                                emit(event)
                            }
                            is LlmStreamEvent.Final -> {
                                rememberSuccessfulTarget(target)
                                emit(event)
                                completed = true
                            }
                            is LlmStreamEvent.Error -> {
                                failureForCandidate = event.throwable ?: IOException(event.message)
                            }
                        }
                    }
                if (completed) return@flow
                val failure = failureForCandidate
                if (failure != null) {
                    lastFailure = failure
                    forgetRememberedTargetIfNeeded(failure)
                    shouldTryNext = !emittedContent && shouldTryNextCandidate(failure, index, targets.lastIndex)
                }
                if (!shouldTryNext) {
                    val resolvedFailure = recoverableFailure(failure)
                    val message = resolvedFailure?.message
                        ?: failure?.message
                        ?: "${profile.title} stream finished without a final response."
                    emit(LlmStreamEvent.Error(message, resolvedFailure ?: failure ?: IOException(message)))
                    return@flow
                }
            }
            val resolvedFailure = recoverableFailure(lastFailure)
            val message = resolvedFailure?.message
                ?: lastFailure?.message
                ?: "${profile.title} stream failed for all endpoint candidates."
            emit(LlmStreamEvent.Error(message, resolvedFailure ?: lastFailure ?: IOException(message)))
        }
    }

    private fun plannedTargets(): List<ProviderExecutionTarget> {
        val planned = ProviderEndpointPlanner.planTargets(
            profile = profile,
            requestedProtocol = config.providerProtocol,
            rawBaseUrl = config.baseUrl
        )
        val cached = successfulTargetCache[cacheKey()]
            ?: resolutionStore?.load(cacheKey())
        return if (cached == null) {
            planned
        } else {
            listOf(cached) + planned.filterNot {
                it.protocol == cached.protocol && it.endpointUrl == cached.endpointUrl
            }
        }
    }

    private fun createDelegate(target: ProviderExecutionTarget): LlmProvider {
        return when (target.protocol) {
            ProviderProtocol.OpenAi -> OpenAiCompatibleProvider(
                providerLabel = profile.title,
                apiKey = config.apiKey,
                model = config.model,
                client = client,
                baseUrl = target.endpointUrl,
                extraHeaders = profile.extraHeaders,
                cacheMode = profile.cacheMode
            )

            ProviderProtocol.OpenAiResponses -> OpenAiResponsesProvider(
                providerLabel = profile.title,
                apiKey = config.apiKey,
                model = config.model,
                client = client,
                baseUrl = target.endpointUrl,
                extraHeaders = profile.extraHeaders
            )

            ProviderProtocol.Anthropic -> AnthropicCompatibleProvider(
                providerLabel = profile.title,
                apiKey = config.apiKey,
                model = config.model,
                client = client,
                baseUrl = target.endpointUrl,
                extraHeaders = profile.extraHeaders
            )
        }
    }

    private fun shouldTryNextCandidate(
        throwable: Throwable,
        index: Int,
        lastIndex: Int
    ): Boolean {
        if (index >= lastIndex) return false
        val httpFailure = throwable as? ProviderHttpException ?: return false
        return httpFailure.isRetryableCandidateFailure
    }

    private fun forgetRememberedTargetIfNeeded(throwable: Throwable?) {
        val httpFailure = throwable as? ProviderHttpException ?: return
        if (!httpFailure.isRetryableCandidateFailure && !httpFailure.isAuthenticationRecoveryFailure) return
        successfulTargetCache.remove(cacheKey())
        resolutionStore?.forget(cacheKey())
    }

    private fun recoverableFailure(throwable: Throwable?): Throwable? {
        val httpFailure = throwable as? ProviderHttpException ?: return throwable
        if (!httpFailure.isAuthenticationRecoveryFailure) return httpFailure
        return IOException(
            "${profile.title} 认证/连接恢复失败：已清理本地记忆端点并尝试候选接口，但服务仍返回 HTTP 401。请检查 API Key、账号连接状态、Base URL，或先在该服务侧完成 connect()。原始信息：${httpFailure.responseBody.take(600)}",
            httpFailure
        )
    }

    private fun rememberSuccessfulTarget(target: ProviderExecutionTarget) {
        successfulTargetCache[cacheKey()] = target
        resolutionStore?.remember(cacheKey(), target)
    }

    private fun cacheKey(): String {
        return ProviderResolutionStore.cacheKeyFor(
            configId = config.activeProviderConfigId,
            providerName = profile.id,
            baseUrl = config.baseUrl,
            model = config.model
        )
    }

    companion object {
        private val successfulTargetCache = ConcurrentHashMap<String, ProviderExecutionTarget>()

        fun clearRememberedTargets(prefix: String) {
            val normalizedPrefix = prefix.trim()
            if (normalizedPrefix.isBlank()) return
            successfulTargetCache.keys
                .filter { it.startsWith(normalizedPrefix) }
                .forEach { successfulTargetCache.remove(it) }
        }
    }
}
