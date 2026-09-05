// ExoPlayer 包装（§A.1 TTS 粤语男声流式播放）
package com.elder.android.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class TtsPlayer {
    private var player: ExoPlayer? = null

    fun play(context: Context, url: String) {
        stop()
        val p = ExoPlayer.Builder(context).build()
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
        player = p
    }

    fun stop() {
        player?.release()
        player = null
    }
}
