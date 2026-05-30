package com.lgclaw.channels

import com.lgclaw.bus.InboundMessage
import com.lgclaw.bus.OutboundMessage
import kotlinx.coroutines.CoroutineScope

interface ChannelAdapter {
    val channelName: String
    val adapterKey: String
    fun start(scope: CoroutineScope, publishInbound: suspend (InboundMessage) -> Unit)
    fun canHandleOutbound(message: OutboundMessage): Boolean
    suspend fun send(message: OutboundMessage)
    suspend fun beginInboundProcessing(message: InboundMessage): String? = null
    suspend fun endInboundProcessing(message: InboundMessage, handle: String?) {}
    fun reconfigureFrom(next: ChannelAdapter): Boolean = false
    fun stop()
}

