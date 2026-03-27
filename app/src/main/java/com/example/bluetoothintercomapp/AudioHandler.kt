package com.example.bluetoothintercomapp

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.io.IOException

class AudioHandler(private val onAudioDataCaptured: (ByteArray) -> Unit) {

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var isPlaying = false

    private val sampleRate = 16000 // 16KHz, good for voice
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO

    private var bufferSizeIn: Int = 0
    private var bufferSizeOut: Int = 0

    companion object {
        private const val TAG = "AudioHandler"
    }

    init {
        bufferSizeIn = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
        bufferSizeOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)
    }

    fun startRecording() {
        if (isRecording) return

        if (bufferSizeIn == AudioRecord.ERROR_BAD_VALUE || bufferSizeIn == AudioRecord.ERROR) {
            Log.e(TAG, "AudioRecord.getMinBufferSize returned error for input")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfigIn,
            audioFormat,
            bufferSizeIn
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        Thread {
            val buffer = ByteArray(bufferSizeIn)
            while (isRecording) {
                val bytesRead = audioRecord?.read(buffer, 0, bufferSizeIn) ?: 0
                if (bytesRead > 0) {
                    onAudioDataCaptured(buffer.copyOf(bytesRead))
                }
            }
        }.start()
        Log.d(TAG, "Audio recording started")
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Audio recording stopped")
    }

    fun startPlaying() {
        if (isPlaying) return

        if (bufferSizeOut == AudioTrack.ERROR_BAD_VALUE || bufferSizeOut == AudioTrack.ERROR) {
            Log.e(TAG, "AudioTrack.getMinBufferSize returned error for output")
            return
        }

        audioTrack = AudioTrack(
            AudioManager.STREAM_VOICE_CALL, // Use VOICE_CALL stream for SCO
            sampleRate,
            channelConfigOut,
            audioFormat,
            bufferSizeOut,
            AudioTrack.MODE_STREAM
        )

        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack initialization failed")
            return
        }

        audioTrack?.play()
        isPlaying = true
        Log.d(TAG, "Audio playing started")
    }

    fun playAudio(audioData: ByteArray) {
        if (isPlaying) {
            try {
                audioTrack?.write(audioData, 0, audioData.size)
            } catch (e: IOException) {
                Log.e(TAG, "Error writing audio data to AudioTrack", e)
            }
        }
    }

    fun stopPlaying() {
        if (!isPlaying) return
        isPlaying = false
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        Log.d(TAG, "Audio playing stopped")
    }

    fun release() {
        stopRecording()
        stopPlaying()
    }
}
