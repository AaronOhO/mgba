/* Copyright (c) 2026 Jeffrey Pfau
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package io.mgba.android.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

class AndroidAudioSink {
    private var audioTrack: AudioTrack? = null
    private var sampleRate = 0

    @Synchronized
    fun start(requestedSampleRate: Int) {
        if (requestedSampleRate <= 0) return
        if (audioTrack == null || requestedSampleRate != sampleRate) {
            release()
            sampleRate = requestedSampleRate
            val minimumSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferSize = audioBufferSize(minimumSize, sampleRate)
            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
        }
        audioTrack?.play()
    }

    @Synchronized
    fun write(samples: ShortArray, frameCount: Int, sync: Boolean = false) {
        val track = audioTrack ?: return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) return
        track.write(
            samples,
            0,
            frameCount * 2,
            if (sync) AudioTrack.WRITE_BLOCKING else AudioTrack.WRITE_NON_BLOCKING,
        )
    }

    @Synchronized
    fun pause() {
        audioTrack?.let {
            if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                it.pause()
            }
            it.flush()
        }
    }

    @Synchronized
    fun release() {
        audioTrack?.release()
        audioTrack = null
        sampleRate = 0
    }
}

internal fun audioBufferSize(minimumSize: Int, sampleRate: Int): Int {
    val requestedSize = maxOf(minimumSize, sampleRate * 4 / 10)
    return (requestedSize + 3) and 3.inv()
}
