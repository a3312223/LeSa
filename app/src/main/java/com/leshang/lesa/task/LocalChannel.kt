package com.leshang.lesa.task

import com.leshang.lesa.core.run.Channel
import com.leshang.lesa.core.run.ChannelInput
import com.leshang.lesa.core.run.ChannelInputSink
import com.leshang.lesa.core.run.ChannelPeer
import com.leshang.lesa.core.run.Delivery
import com.leshang.lesa.core.run.SendOutcome

/** 手机上的任务卡片和通知：任务服务仍绘制现有界面，此适配器承接送达账本。 */
class LocalChannel(private val deliverLocal: suspend (ChannelPeer, Delivery) -> SendOutcome) : Channel {
    override val id = ID
    override val confirms = true
    private var sink: ChannelInputSink? = null

    override fun start(sink: ChannelInputSink): AutoCloseable {
        this.sink = sink
        return AutoCloseable { if (this.sink === sink) this.sink = null }
    }

    override suspend fun send(peer: ChannelPeer, delivery: Delivery): SendOutcome = deliverLocal(peer, delivery)
    suspend fun receive(input: ChannelInput) { sink?.receive(input) }

    companion object { const val ID = "local" }
}
