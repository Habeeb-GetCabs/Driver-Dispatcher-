package com.example.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.math.PI

data class IlaiyaraajaTrackInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val emoji: String
)

object IlaiyaraajaRingtonePlayer {

    private const val TAG = "IlaiyaraajaRingtone"
    private const val SAMPLE_RATE = 22050

    val AVAILABLE_TRACKS = listOf(
        IlaiyaraajaTrackInfo(
            id = "ACCORDION_GROOVE",
            title = "Ilaiyaraaja High-Energy Accordion",
            subtitle = "Upbeat Accordion Beat",
            description = "High-energy accordion melody designed to boost taxi driver alertness in Coimbatore traffic.",
            emoji = "🪗"
        ),
        IlaiyaraajaTrackInfo(
            id = "NAYAKAN_MASS",
            title = "Nayakan Mass BGM Theme",
            subtitle = "Iconic Tamil Cinema Mass Theme",
            description = "Legendary orchestral brass and high-tempo mass rhythm.",
            emoji = "🎷"
        ),
        IlaiyaraajaTrackInfo(
            id = "AGNI_FLUTE",
            title = "Agni Natchathiram Fast Flute Beat",
            subtitle = "Upbeat Synth Flute & Bass",
            description = "Energetic electronic flute theme with fast tempo.",
            emoji = "🪈"
        ),
        IlaiyaraajaTrackInfo(
            id = "THALAPATHI_INTRO",
            title = "Thalapathi Mass Intro Theme",
            subtitle = "Brass Trumpet & Percussion Hit",
            description = "Bold, authoritative intro beat that grabs attention instantly.",
            emoji = "🎺"
        ),
        IlaiyaraajaTrackInfo(
            id = "KARAGATTAKARAN",
            title = "Karagattakaran Folk Rhythm",
            subtitle = "Coimbatore Folk Nadaswaram Groove",
            description = "Lively traditional rural South Indian folk beat.",
            emoji = "🥁"
        ),
        IlaiyaraajaTrackInfo(
            id = "MUNDHINAM_FLUTE",
            title = "Mundhinam Paarthene Magic Flute",
            subtitle = "Uplifting Melodic Flute Harmony",
            description = "Relaxing yet invigorating Raja magic flute tone.",
            emoji = "🎵"
        ),
        IlaiyaraajaTrackInfo(
            id = "COIMBATORE_BEAT",
            title = "Coimbatore Special Raja Beat",
            subtitle = "High-Energy Driver Alert Groove",
            description = "Custom Coimbatore taxi driver energy boost loop.",
            emoji = "⚡"
        )
    )

    private var playingJob: Job? = null
    private var currentAudioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Synchronized
    fun playLoop(trackId: String) {
        stop() // Stop any ongoing playback

        playingJob = scope.launch {
            try {
                val track = AVAILABLE_TRACKS.find { it.id == trackId } ?: AVAILABLE_TRACKS.first()
                val sequence = getSequenceForTrack(track.id)

                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val audioTrack = AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize * 2,
                    AudioTrack.MODE_STREAM
                )

                currentAudioTrack = audioTrack
                audioTrack.play()

                Log.d(TAG, "Started looping Ilaiyaraaja track: ${track.title}")

                // Loop continuously until cancelled
                while (isActive) {
                    for (note in sequence) {
                        if (!isActive) break
                        val samples = generateNoteSamples(note.freq, note.durationMs, note.timbre)
                        var bytesWritten = 0
                        while (bytesWritten < samples.size && isActive) {
                            val ret = audioTrack.write(samples, bytesWritten, samples.size - bytesWritten)
                            if (ret <= 0) break
                            bytesWritten += ret
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack playback error: ${e.message}")
            } finally {
                cleanupAudioTrack()
            }
        }
    }

    @Synchronized
    fun stop() {
        playingJob?.cancel()
        playingJob = null
        cleanupAudioTrack()
    }

    private fun cleanupAudioTrack() {
        try {
            currentAudioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up AudioTrack: ${e.message}")
        } finally {
            currentAudioTrack = null
        }
    }

    private enum class Timbre { ACCORDION, BRASS, FLUTE, FOLK, SYNTH }

    private data class Note(val freq: Double, val durationMs: Int, val timbre: Timbre)

    private fun getSequenceForTrack(trackId: String): List<Note> {
        val E4 = 329.63; val F4 = 349.23; val G4 = 392.00; val A4 = 440.00; val B4 = 493.88
        val C5 = 523.25; val D5 = 587.33; val E5 = 659.25; val F5 = 698.46; val Fs5 = 739.99
        val G5 = 783.99; val Gs5 = 830.61; val A5 = 880.00; val B5 = 987.77
        val C6 = 1046.50; val D6 = 1174.66; val E6 = 1318.51; val REST = 0.0

        return when (trackId) {
            "ACCORDION_GROOVE" -> listOf(
                Note(E5, 180, Timbre.ACCORDION), Note(G5, 180, Timbre.ACCORDION), Note(A5, 180, Timbre.ACCORDION), Note(B5, 250, Timbre.ACCORDION),
                Note(A5, 180, Timbre.ACCORDION), Note(G5, 180, Timbre.ACCORDION), Note(E5, 250, Timbre.ACCORDION), Note(REST, 60, Timbre.ACCORDION),
                Note(D5, 180, Timbre.ACCORDION), Note(E5, 180, Timbre.ACCORDION), Note(G5, 180, Timbre.ACCORDION), Note(A5, 250, Timbre.ACCORDION),
                Note(C6, 220, Timbre.ACCORDION), Note(B5, 180, Timbre.ACCORDION), Note(A5, 180, Timbre.ACCORDION), Note(G5, 250, Timbre.ACCORDION)
            )
            "NAYAKAN_MASS" -> listOf(
                Note(C5, 200, Timbre.BRASS), Note(E5, 200, Timbre.BRASS), Note(G5, 300, Timbre.BRASS), Note(G5, 200, Timbre.BRASS),
                Note(F5, 180, Timbre.BRASS), Note(E5, 180, Timbre.BRASS), Note(D5, 250, Timbre.BRASS), Note(REST, 80, Timbre.BRASS),
                Note(C5, 200, Timbre.BRASS), Note(E5, 200, Timbre.BRASS), Note(G5, 250, Timbre.BRASS), Note(C6, 300, Timbre.BRASS),
                Note(B5, 200, Timbre.BRASS), Note(A5, 200, Timbre.BRASS), Note(G5, 350, Timbre.BRASS)
            )
            "AGNI_FLUTE" -> listOf(
                Note(E5, 150, Timbre.FLUTE), Note(Fs5, 150, Timbre.FLUTE), Note(Gs5, 200, Timbre.FLUTE), Note(B5, 200, Timbre.FLUTE),
                Note(C6, 180, Timbre.FLUTE), Note(B5, 180, Timbre.FLUTE), Note(Gs5, 200, Timbre.FLUTE), Note(Fs5, 200, Timbre.FLUTE),
                Note(E5, 220, Timbre.FLUTE), Note(Gs5, 180, Timbre.FLUTE), Note(B5, 220, Timbre.FLUTE), Note(E6, 350, Timbre.FLUTE)
            )
            "THALAPATHI_INTRO" -> listOf(
                Note(A4, 220, Timbre.BRASS), Note(C5, 220, Timbre.BRASS), Note(E5, 280, Timbre.BRASS), Note(A5, 350, Timbre.BRASS),
                Note(G5, 200, Timbre.BRASS), Note(E5, 200, Timbre.BRASS), Note(C5, 220, Timbre.BRASS), Note(A4, 300, Timbre.BRASS),
                Note(D5, 220, Timbre.BRASS), Note(F5, 220, Timbre.BRASS), Note(A5, 400, Timbre.BRASS)
            )
            "KARAGATTAKARAN" -> listOf(
                Note(G4, 160, Timbre.FOLK), Note(C5, 160, Timbre.FOLK), Note(E5, 200, Timbre.FOLK), Note(G5, 220, Timbre.FOLK),
                Note(A5, 180, Timbre.FOLK), Note(G5, 180, Timbre.FOLK), Note(E5, 200, Timbre.FOLK), Note(C5, 220, Timbre.FOLK),
                Note(D5, 160, Timbre.FOLK), Note(E5, 160, Timbre.FOLK), Note(F5, 180, Timbre.FOLK), Note(E5, 180, Timbre.FOLK),
                Note(D5, 200, Timbre.FOLK), Note(C5, 300, Timbre.FOLK)
            )
            "MUNDHINAM_FLUTE" -> listOf(
                Note(E5, 220, Timbre.FLUTE), Note(B5, 220, Timbre.FLUTE), Note(A5, 180, Timbre.FLUTE), Note(Gs5, 180, Timbre.FLUTE),
                Note(A5, 200, Timbre.FLUTE), Note(B5, 220, Timbre.FLUTE), Note(E6, 300, Timbre.FLUTE), Note(D6, 200, Timbre.FLUTE),
                Note(C6, 200, Timbre.FLUTE), Note(B5, 220, Timbre.FLUTE), Note(A5, 220, Timbre.FLUTE), Note(Gs5, 300, Timbre.FLUTE)
            )
            else -> listOf( // COIMBATORE_BEAT
                Note(E5, 160, Timbre.SYNTH), Note(E5, 160, Timbre.SYNTH), Note(G5, 200, Timbre.SYNTH), Note(A5, 220, Timbre.SYNTH),
                Note(A5, 160, Timbre.SYNTH), Note(C6, 220, Timbre.SYNTH), Note(B5, 180, Timbre.SYNTH), Note(A5, 180, Timbre.SYNTH),
                Note(G5, 200, Timbre.SYNTH), Note(E5, 200, Timbre.SYNTH), Note(D5, 180, Timbre.SYNTH), Note(E5, 320, Timbre.SYNTH)
            )
        }
    }

    private fun generateNoteSamples(freq: Double, durationMs: Int, timbre: Timbre): ByteArray {
        val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt().coerceAtLeast(10)
        val sampleByteArray = ByteArray(numSamples * 2)

        if (freq <= 0.0) {
            // Silence/Rest
            return sampleByteArray
        }

        val attackSamples = (numSamples * 0.1).toInt().coerceAtLeast(1)
        val decaySamples = (numSamples * 0.15).toInt().coerceAtLeast(1)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            
            // Envelope (Attack - Sustain - Decay)
            val envelope = when {
                i < attackSamples -> i.toDouble() / attackSamples
                i > (numSamples - decaySamples) -> (numSamples - i).toDouble() / decaySamples
                else -> 1.0
            }

            // Timbre harmonic mix
            val rawVal = when (timbre) {
                Timbre.ACCORDION -> {
                    0.5 * sin(2.0 * PI * freq * t) +
                    0.3 * sin(2.0 * PI * (freq * 2) * t) +
                    0.2 * sin(2.0 * PI * (freq * 3) * t)
                }
                Timbre.BRASS -> {
                    0.4 * sin(2.0 * PI * freq * t) +
                    0.3 * sin(2.0 * PI * (freq * 3) * t) +
                    0.3 * sin(2.0 * PI * (freq * 5) * t)
                }
                Timbre.FLUTE -> {
                    0.8 * sin(2.0 * PI * freq * t) +
                    0.2 * sin(2.0 * PI * (freq * 2) * t)
                }
                Timbre.FOLK -> {
                    0.4 * sin(2.0 * PI * freq * t) +
                    0.4 * sin(2.0 * PI * (freq * 2.02) * t) +
                    0.2 * sin(2.0 * PI * (freq * 4) * t)
                }
                Timbre.SYNTH -> {
                    0.6 * sin(2.0 * PI * freq * t) +
                    0.4 * sin(2.0 * PI * (freq * 1.5) * t)
                }
            }

            val sampleVal = (rawVal * envelope * 24000).toInt().coerceIn(-32768, 32767)
            
            // 16-bit PCM Little Endian
            sampleByteArray[i * 2] = (sampleVal and 0x00FF).toByte()
            sampleByteArray[i * 2 + 1] = ((sampleVal shr 8) and 0x00FF).toByte()
        }

        return sampleByteArray
    }
}
