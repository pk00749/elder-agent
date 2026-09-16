package com.elder.android.screen.interview

import com.elder.android.data.asr.AsrApiClient
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/**
 * ASR 建连期间先缓存 PCM 帧，避免弱网下老人开口后的前几秒丢失。
 */
class AsrSessionBridge(
    private val onPartial: (String) -> Unit,
) {
    private val session = AtomicReference<AsrApiClient.RealtimeAsrSession?>(null)
    private val buffered = Collections.synchronizedList(mutableListOf<ByteArray>())
    @Volatile private var detached = false

    fun onFrame(frame: ByteArray) {
        val current = session.get()
        if (current != null) {
            current.appendAudio(frame)
        } else {
            synchronized(buffered) { buffered.add(frame) }
        }
    }

    fun attach(value: AsrApiClient.RealtimeAsrSession) {
        if (detached) {
            value.close()
            return
        }
        session.set(value)
        val pending = synchronized(buffered) {
            val copy = buffered.toList()
            buffered.clear()
            copy
        }
        pending.forEach(value::appendAudio)
    }

    fun observePartial(text: String) = onPartial(text)

    fun finish(): AsrApiClient.RealtimeAsrSession? {
        detached = true
        return session.getAndSet(null)
    }

    fun close() {
        detached = true
        session.getAndSet(null)?.close()
        synchronized(buffered) { buffered.clear() }
    }
}
