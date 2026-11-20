package com.example.data.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceRecorder(context: Context) {
    private val appContext = context.applicationContext
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): Boolean {
        stopAndDiscard()
        return try {
            val file = File(appContext.cacheDir, "dost_voice_${System.currentTimeMillis()}.m4a")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
            stopAndDiscard()
            false
        }
    }

    fun stopAndGetFile(): File? {
        val file = outputFile
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile = null
            if (file != null && file.exists() && file.length() > 0L) file else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop audio recording cleanly", e)
            runCatching { recorder?.release() }
            recorder = null
            outputFile = null
            runCatching { file?.delete() }
            null
        }
    }

    fun getAmplitudeRatio(): Float {
        val raw = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        return (raw / 16000f).coerceIn(0f, 1f)
    }

    fun stopAndDiscard() {
        runCatching {
            recorder?.stop()
        }
        runCatching {
            recorder?.release()
        }
        recorder = null
        outputFile?.let { runCatching { it.delete() } }
        outputFile = null
    }

    companion object {
        private const val TAG = "VoiceRecorder"
    }
}
