/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class AudioRecorder(private val context: Context) {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate, channelConfig, audioFormat,
    )

    fun hasRecordAudioPermission(): Boolean {
        return context.checkCallingOrSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Starts recording from the microphone and suspends until [stop] is called.
     * Returns the recorded audio as a WAV byte array (16kHz mono 16-bit PCM).
     * Throws IllegalStateException if permission is not granted or AudioRecord fails to initialize.
     */
    suspend fun record(): ByteArray = withContext(Dispatchers.IO) {
        if (!hasRecordAudioPermission()) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("AudioRecord: invalid buffer size")
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize * 2,
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("AudioRecord failed to initialize. Check RECORD_AUDIO permission.")
        }

        audioRecord = record
        record.startRecording()
        isRecording = true

        val allSamples = mutableListOf<Short>()
        val buffer = ShortArray(bufferSize / 2)

        try {
            while (isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) {
                        allSamples.add(buffer[i])
                    }
                    var max = 0
                    for (i in 0 until read) {
                        val abs = kotlin.math.abs(buffer[i].toInt())
                        if (abs > max) max = abs
                    }
                    _amplitude.value = max.toFloat() / Short.MAX_VALUE
                }
            }
        } finally {
            record.stop()
            record.release()
            audioRecord = null
            _amplitude.value = 0f
        }

        pcmToWav(allSamples.toShortArray())
    }

    /**
     * Stops the ongoing recording. The suspended [record] call will return the WAV bytes.
     */
    fun stop() {
        isRecording = false
    }

    private fun pcmToWav(pcmData: ShortArray): ByteArray {
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = pcmData.size * blockAlign
        val totalSize = 36 + dataSize

        val buffer = ByteArrayOutputStream()
        val out = DataOutputStream(buffer)

        // RIFF header
        out.writeBytes("RIFF")
        writeInt(out, totalSize)
        out.writeBytes("WAVE")

        // fmt chunk
        out.writeBytes("fmt ")
        writeInt(out, 16) // chunk size
        writeShort(out, 1) // PCM format
        writeShort(out, numChannels.toShort())
        writeInt(out, sampleRate)
        writeInt(out, byteRate)
        writeShort(out, blockAlign.toShort())
        writeShort(out, bitsPerSample.toShort())

        // data chunk
        out.writeBytes("data")
        writeInt(out, dataSize)
        for (sample in pcmData) {
            writeShort(out, sample)
        }

        out.flush()
        return buffer.toByteArray()
    }

    private fun writeInt(out: DataOutputStream, value: Int) {
        out.writeByte(value and 0xff)
        out.writeByte((value shr 8) and 0xff)
        out.writeByte((value shr 16) and 0xff)
        out.writeByte((value shr 24) and 0xff)
    }

    private fun writeShort(out: DataOutputStream, value: Short) {
        out.writeByte(value.toInt() and 0xff)
        out.writeByte((value.toInt() shr 8) and 0xff)
    }
}
