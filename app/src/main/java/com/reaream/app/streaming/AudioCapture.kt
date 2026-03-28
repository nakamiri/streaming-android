package com.reaream.app.streaming

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*

class AudioCapture(
    private val onAudioData: (ByteArray, Long) -> Unit,
) {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isMuted: Boolean = false

    fun start(context: android.content.Context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Audio permission not granted")
            return
        }

        // Stop any existing capture before starting a new one
        if (audioRecord != null) {
            stop()
        }

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()

        captureJob = scope.launch {
            val buffer = ByteArray(4096)
            val startNanos = System.nanoTime()
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val timestampUs = (System.nanoTime() - startNanos) / 1000
                    if (isMuted) {
                        onAudioData(ByteArray(read), timestampUs)
                    } else {
                        onAudioData(buffer.copyOf(read), timestampUs)
                    }
                }
            }
        }

        Log.i(TAG, "Audio capture started")
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping audio capture", e)
        }
        audioRecord = null
        Log.i(TAG, "Audio capture stopped")
    }

    fun release() {
        stop()
        scope.cancel()
    }

    companion object {
        private const val TAG = "AudioCapture"
    }
}
