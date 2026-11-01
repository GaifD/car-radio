package com.gaifd.carradio.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Выравнивание громкости: медленно подтягивает средний уровень станции к общему.
 * Медленно — чтобы не «качало» внутри песни: шаг не больше ~1 дБ в секунду.
 * Сверху мягкий ограничитель, чтобы усиленный звук не хрипел.
 * Когда выключено — коэффициент плавно возвращается к 1, звук не трогается.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class Leveler(initialGain: Float) : BaseAudioProcessor() {

    @Volatile var enabled = false
    @Volatile var gain = initialGain.coerceIn(MIN_GAIN, MAX_GAIN)
        private set

    private var channels = 2
    private var sampleRate = 44100
    private var meanSquare = -1.0 // сглаженный средний квадрат, -1 = ещё не мерили

    private companion object {
        const val TARGET_RMS = 0.1 * 32768.0     // около −20 dBFS
        const val SILENCE_RMS = 0.003 * 32768.0  // тишину и паузы не учитываем
        const val MIN_GAIN = 0.5f                // −6 дБ
        const val MAX_GAIN = 2.8f                // +9 дБ
        const val MEASURE_SEC = 4.0              // окно измерения
        const val MAX_DB_PER_SEC = 1.0
        const val LIMIT = 0.89                   // от этого уровня начинаем мягко срезать пики
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val out = replaceOutputBuffer(bytes)
        val frames = bytes / (2 * channels)
        val start = input.position()

        // 1) Меряем уровень этого куска.
        var sum = 0.0
        for (i in 0 until frames * channels) {
            val s = input.getShort(start + i * 2).toDouble()
            sum += s * s
        }
        val ms = if (frames > 0) sum / (frames * channels) else 0.0
        val dt = frames.toDouble() / sampleRate

        // 2) Обновляем целевое усиление.
        var g = gain
        if (enabled) {
            if (sqrt(ms) > SILENCE_RMS) {
                val a = 1 - exp(-dt / MEASURE_SEC)
                meanSquare = if (meanSquare < 0) ms else meanSquare + a * (ms - meanSquare)
            }
            if (meanSquare > 0) {
                val want = (TARGET_RMS / sqrt(meanSquare)).toFloat().coerceIn(MIN_GAIN, MAX_GAIN)
                g = stepTowards(g, want, dt)
            }
        } else {
            g = stepTowards(g, 1f, dt)
        }
        val g0 = gain
        gain = g

        // 3) Применяем: плавно от старого коэффициента к новому внутри куска.
        val total = frames * channels
        for (i in 0 until total) {
            val k = g0 + (g - g0) * (i.toFloat() / total)
            val s = input.getShort(start + i * 2) * k / 32768.0
            out.putShort((softLimit(s) * 32767.0).toInt().toShort())
        }
        input.position(start + bytes)
        out.flip()
    }

    override fun onFlush() {
        // Позицию измерения сохраняем — новая песня той же станции обычно того же уровня.
    }

    private fun stepTowards(cur: Float, want: Float, dt: Double): Float {
        val maxRatio = Math.pow(10.0, MAX_DB_PER_SEC * dt / 20.0).toFloat()
        return when {
            want > cur -> minOf(want, cur * maxRatio)
            want < cur -> maxOf(want, cur / maxRatio)
            else -> cur
        }
    }

    private fun softLimit(x: Double): Double {
        val ax = abs(x)
        if (ax <= LIMIT) return x
        val over = (ax - LIMIT) / (1 - LIMIT)
        val y = LIMIT + (1 - LIMIT) * tanh(over)
        return if (x < 0) -y else y
    }
}
