package com.reaream.app.streaming

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.reaream.app.data.model.AudioInputMode
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt

class AudioCapture(
    private val onAudioData: (ByteArray, Long, Float, Float) -> Unit,
) {
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isMuted: Boolean = false

    @Volatile
    var gain: Float = 1f

    @Volatile
    var inputMode: AudioInputMode = AudioInputMode.MICROPHONE

    @Volatile
    var toneFrequencyHz: Int = DEFAULT_TONE_FREQUENCY_HZ

    fun start(context: android.content.Context): Boolean {
        // Stop any existing capture before starting a new one
        if (audioRecord != null || captureJob != null) {
            stop()
        }

        val sampleRate = SAMPLE_RATE
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid AudioRecord min buffer size: $minBufferSize")
            return false
        }
        val bufferSize = ((maxOf(minBufferSize * 2, DEFAULT_BUFFER_SIZE_BYTES) + BYTES_PER_FRAME - 1) / BYTES_PER_FRAME) * BYTES_PER_FRAME

        if (inputMode == AudioInputMode.TEST_TONE) {
            startTestToneCapture(bufferSize)
            Log.i(TAG, "Audio capture started")
            return true
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Audio permission not granted")
            return false
        }

        val record = createAudioRecord(sampleRate, channelConfig, audioFormat, bufferSize) ?: run {
            Log.e(TAG, "AudioRecord initialization failed for all sources")
            return false
        }

        audioRecord = record

        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            stop()
            return false
        }

        captureJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            var framesCaptured = 0L
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: -1
                if (read > 0) {
                    val frameAlignedRead = read - (read % BYTES_PER_FRAME)
                    if (frameAlignedRead <= 0) continue
                    val timestampUs = framesCaptured * MICROS_PER_SECOND / SAMPLE_RATE
                    val inputLevel = calculatePcm16Level(buffer, frameAlignedRead)
                    if (isMuted) {
                        onAudioData(ByteArray(frameAlignedRead), timestampUs, inputLevel, 0f)
                    } else {
                        val output = applyGain(buffer, frameAlignedRead, gain)
                        onAudioData(output, timestampUs, inputLevel, calculatePcm16Level(output, frameAlignedRead))
                    }
                    framesCaptured += frameAlignedRead / BYTES_PER_FRAME
                } else if (read < 0 && read != AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.w(TAG, "AudioRecord read failed: $read")
                }
            }
        }

        Log.i(TAG, "Audio capture started")
        return true
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

    private fun startTestToneCapture(bufferSize: Int) {
        val chunkSize = ((maxOf(bufferSize, TEST_TONE_CHUNK_BYTES) + BYTES_PER_FRAME - 1) / BYTES_PER_FRAME) * BYTES_PER_FRAME
        Log.i(TAG, "Using test tone audio source: ${toneFrequencyHz.coerceIn(MIN_TONE_FREQUENCY_HZ, MAX_TONE_FREQUENCY_HZ)}Hz")
        captureJob = scope.launch {
            var framesCaptured = 0L
            var phase = 0.0
            while (isActive) {
                val angularStep = 2.0 * PI * toneFrequencyHz.coerceIn(MIN_TONE_FREQUENCY_HZ, MAX_TONE_FREQUENCY_HZ) / SAMPLE_RATE
                val timestampUs = framesCaptured * MICROS_PER_SECOND / SAMPLE_RATE
                val generated = generateTonePcm16(chunkSize, gain, angularStep, phase)
                phase = generated.nextPhase
                val inputLevel = calculatePcm16Level(generated.pcm, generated.pcm.size)
                val pcm = if (isMuted) ByteArray(chunkSize) else generated.pcm
                val outputLevel = if (isMuted) 0f else inputLevel
                onAudioData(pcm, timestampUs, inputLevel, outputLevel)
                framesCaptured += chunkSize / BYTES_PER_FRAME
                delay((chunkSize / BYTES_PER_FRAME) * MILLIS_PER_SECOND / SAMPLE_RATE)
            }
        }
    }

    private fun createAudioRecord(
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSize: Int,
    ): AudioRecord? {
        for (audioSource in AUDIO_SOURCES) {
            val record = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "Using audio source: ${audioSourceName(audioSource)}")
                return record
            }
            Log.w(TAG, "AudioRecord init failed for source: ${audioSourceName(audioSource)}")
            record.release()
        }
        return null
    }

    companion object {
        private const val TAG = "AudioCapture"
        private const val SAMPLE_RATE = 44100
        private const val BYTES_PER_FRAME = 2
        private const val DEFAULT_BUFFER_SIZE_BYTES = 4096
        private const val TEST_TONE_CHUNK_BYTES = 2048
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val MILLIS_PER_SECOND = 1000L
        private const val DEFAULT_TONE_FREQUENCY_HZ = 1000
        private const val MIN_TONE_FREQUENCY_HZ = 220
        private const val MAX_TONE_FREQUENCY_HZ = 2000
        private val AUDIO_SOURCES = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
        )
    }
}

private fun audioSourceName(audioSource: Int): String = when (audioSource) {
    MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
    MediaRecorder.AudioSource.MIC -> "MIC"
    else -> audioSource.toString()
}

internal data class GeneratedTonePcm(
    val pcm: ByteArray,
    val nextPhase: Double,
)

internal fun calculatePcm16Level(source: ByteArray, size: Int): Float {
    if (size <= 1) return 0f
    var sumSquares = 0.0
    var samples = 0
    var index = 0
    while (index + 1 < size) {
        val sample = ((source[index + 1].toInt() shl 8) or (source[index].toInt() and 0xFF)).toShort().toInt()
        sumSquares += sample.toDouble() * sample.toDouble()
        samples++
        index += 2
    }
    if (samples == 0) return 0f
    val rms = kotlin.math.sqrt(sumSquares / samples)
    return (rms / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
}

internal fun generateTonePcm16(
    size: Int,
    gain: Float,
    angularStep: Double,
    phase: Double,
): GeneratedTonePcm {
    val output = ByteArray(size)
    val amplitude = (Short.MAX_VALUE * 0.25f * gain.coerceAtLeast(0f)).roundToInt()
    var currentPhase = phase
    var index = 0
    while (index + 1 < size) {
        val sample = (sin(currentPhase) * amplitude)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        output[index] = (sample and 0xFF).toByte()
        output[index + 1] = ((sample shr 8) and 0xFF).toByte()
        currentPhase += angularStep
        if (currentPhase >= 2.0 * PI) currentPhase -= 2.0 * PI
        index += 2
    }
    return GeneratedTonePcm(output, currentPhase)
}

internal fun applyGain(source: ByteArray, size: Int, gain: Float): ByteArray {
    val output = source.copyOf(size)
    if (gain == 1f) return output

    var index = 0
    while (index + 1 < size) {
        val sample = ((output[index + 1].toInt() shl 8) or (output[index].toInt() and 0xFF)).toShort().toInt()
        val scaled = (sample * gain)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        output[index] = (scaled and 0xFF).toByte()
        output[index + 1] = ((scaled shr 8) and 0xFF).toByte()
        index += 2
    }
    return output
}
