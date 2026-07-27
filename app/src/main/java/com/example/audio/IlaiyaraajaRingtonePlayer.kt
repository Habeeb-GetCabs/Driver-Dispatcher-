package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.sin
import kotlin.math.PI
import kotlin.random.Random

data class IlaiyaraajaTrackInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val emoji: String
)

object IlaiyaraajaRingtonePlayer {

    private const val TAG = "IlaiyaraajaRingtone"
    private const val SAMPLE_RATE = 44100

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
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Synchronized
    fun playLoop(trackId: String) {
        playLoop(null, trackId)
    }

    @Synchronized
    fun playLoop(context: Context?, trackId: String = "ACCORDION_GROOVE") {
        stop() // Stop any ongoing playback

        // 1. Check if user has uploaded a custom raw MP3 file or asset in app resources
        if (context != null) {
            val playedMp3 = tryPlayCustomMp3Resource(context)
            if (playedMp3) {
                Log.d(TAG, "Successfully started looping custom MP3 audio resource!")
                return
            }
        }

        // 2. Synthesize audio loop if no custom MP3 resource was found
        playingJob = scope.launch {
            try {
                val track = AVAILABLE_TRACKS.find { it.id == trackId } ?: AVAILABLE_TRACKS.first()
                val sequence = getSequenceForTrack(track.id)

                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(8192)

                val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(SAMPLE_RATE)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                .build()
                        )
                        .setBufferSizeInBytes(minBufferSize * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBufferSize * 2,
                        AudioTrack.MODE_STREAM
                    )
                }

                currentAudioTrack = audioTrack
                audioTrack.play()

                Log.d(TAG, "Started looping Ilaiyaraaja synthesized track: ${track.title}")

                // Loop continuously until cancelled
                while (isActive) {
                    for ((noteIdx, note) in sequence.withIndex()) {
                        if (!isActive) break
                        val samples = generateStereoNoteSamples(note.freq, note.durationMs, note.timbre, noteIdx % 4 == 0)
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

    private fun tryPlayCustomMp3Resource(context: Context): Boolean {
        return try {
            val rawResNames = listOf(
                "ilaiyaraaja_bgm",
                "dispatch_alert",
                "custom_ringtone",
                "bgm",
                "ringtone"
            )
            var rawResId = 0
            for (resName in rawResNames) {
                val id = context.resources.getIdentifier(resName, "raw", context.packageName)
                if (id != 0) {
                    rawResId = id
                    break
                }
            }

            if (rawResId != 0) {
                val player = MediaPlayer.create(context, rawResId)
                if (player != null) {
                    player.isLooping = true
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    player.start()
                    mediaPlayer = player
                    Log.d(TAG, "Playing custom raw resource MP3 (resId: $rawResId)")
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play custom MP3 resource: ${e.message}")
            false
        }
    }

    @Synchronized
    fun stop() {
        playingJob?.cancel()
        playingJob = null
        cleanupAudioTrack()
        cleanupMediaPlayer()
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

    private fun cleanupMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }

    private enum class Timbre { ACCORDION, BRASS, FLUTE, FOLK, SYNTH }

    private data class Note(val freq: Double, val durationMs: Int, val timbre: Timbre)

    private fun getSequenceForTrack(trackId: String): List<Note> {
        val C4 = 261.63; val D4 = 293.66; val Eb4 = 311.13; val E4 = 329.63; val F4 = 349.23; val G4 = 392.00; val Ab4 = 415.30; val A4 = 440.00; val Bb4 = 466.16; val B4 = 493.88
        val C5 = 523.25; val Db5 = 554.37; val D5 = 587.33; val Eb5 = 622.25; val E5 = 659.25; val F5 = 698.46; val Fs5 = 739.99
        val G5 = 783.99; val Gs5 = 830.61; val Ab5 = 830.61; val A5 = 880.00; val Bb5 = 932.33; val B5 = 987.77
        val C6 = 1046.50; val Cs6 = 1108.73; val D6 = 1174.66; val E6 = 1318.51; val REST = 0.0

        return when (trackId) {
            "ACCORDION_GROOVE" -> listOf(
                Note(E5, 200, Timbre.ACCORDION), Note(G5, 200, Timbre.ACCORDION), Note(A5, 200, Timbre.ACCORDION), Note(B5, 280, Timbre.ACCORDION),
                Note(A5, 200, Timbre.ACCORDION), Note(G5, 200, Timbre.ACCORDION), Note(E5, 280, Timbre.ACCORDION), Note(REST, 80, Timbre.ACCORDION),
                Note(D5, 200, Timbre.ACCORDION), Note(E5, 200, Timbre.ACCORDION), Note(G5, 200, Timbre.ACCORDION), Note(A5, 280, Timbre.ACCORDION),
                Note(C6, 240, Timbre.ACCORDION), Note(B5, 200, Timbre.ACCORDION), Note(A5, 200, Timbre.ACCORDION), Note(G5, 300, Timbre.ACCORDION)
            )
            "NAYAKAN_MASS" -> listOf(
                Note(C5, 220, Timbre.BRASS), Note(D5, 220, Timbre.BRASS), Note(Eb5, 300, Timbre.BRASS), Note(G5, 300, Timbre.BRASS),
                Note(F5, 200, Timbre.BRASS), Note(Eb5, 200, Timbre.BRASS), Note(D5, 280, Timbre.BRASS), Note(REST, 100, Timbre.BRASS),
                Note(C5, 220, Timbre.BRASS), Note(Eb5, 220, Timbre.BRASS), Note(G5, 280, Timbre.BRASS), Note(C6, 350, Timbre.BRASS),
                Note(Bb5, 220, Timbre.BRASS), Note(Ab5, 220, Timbre.BRASS), Note(G5, 400, Timbre.BRASS)
            )
            "AGNI_FLUTE" -> listOf(
                Note(E5, 180, Timbre.FLUTE), Note(Fs5, 180, Timbre.FLUTE), Note(Gs5, 220, Timbre.FLUTE), Note(B5, 220, Timbre.FLUTE),
                Note(Cs6, 200, Timbre.FLUTE), Note(B5, 200, Timbre.FLUTE), Note(Gs5, 220, Timbre.FLUTE), Note(Fs5, 220, Timbre.FLUTE),
                Note(E5, 250, Timbre.FLUTE), Note(Gs5, 200, Timbre.FLUTE), Note(B5, 250, Timbre.FLUTE), Note(E6, 400, Timbre.FLUTE)
            )
            "THALAPATHI_INTRO" -> listOf(
                Note(A4, 240, Timbre.BRASS), Note(C5, 240, Timbre.BRASS), Note(E5, 300, Timbre.BRASS), Note(A5, 380, Timbre.BRASS),
                Note(G5, 220, Timbre.BRASS), Note(E5, 220, Timbre.BRASS), Note(C5, 240, Timbre.BRASS), Note(A4, 320, Timbre.BRASS),
                Note(D5, 240, Timbre.BRASS), Note(F5, 240, Timbre.BRASS), Note(A5, 450, Timbre.BRASS)
            )
            "KARAGATTAKARAN" -> listOf(
                Note(G4, 180, Timbre.FOLK), Note(C5, 180, Timbre.FOLK), Note(E5, 220, Timbre.FOLK), Note(G5, 250, Timbre.FOLK),
                Note(A5, 200, Timbre.FOLK), Note(G5, 200, Timbre.FOLK), Note(E5, 220, Timbre.FOLK), Note(C5, 250, Timbre.FOLK),
                Note(D5, 180, Timbre.FOLK), Note(E5, 180, Timbre.FOLK), Note(F5, 200, Timbre.FOLK), Note(E5, 200, Timbre.FOLK),
                Note(D5, 220, Timbre.FOLK), Note(C5, 350, Timbre.FOLK)
            )
            "MUNDHINAM_FLUTE" -> listOf(
                Note(E5, 240, Timbre.FLUTE), Note(B5, 240, Timbre.FLUTE), Note(A5, 200, Timbre.FLUTE), Note(Gs5, 200, Timbre.FLUTE),
                Note(A5, 220, Timbre.FLUTE), Note(B5, 240, Timbre.FLUTE), Note(E6, 350, Timbre.FLUTE), Note(D6, 220, Timbre.FLUTE),
                Note(C6, 220, Timbre.FLUTE), Note(B5, 240, Timbre.FLUTE), Note(A5, 240, Timbre.FLUTE), Note(Gs5, 350, Timbre.FLUTE)
            )
            else -> listOf( // COIMBATORE_BEAT
                Note(E5, 180, Timbre.SYNTH), Note(E5, 180, Timbre.SYNTH), Note(G5, 220, Timbre.SYNTH), Note(A5, 250, Timbre.SYNTH),
                Note(A5, 180, Timbre.SYNTH), Note(C6, 240, Timbre.SYNTH), Note(B5, 200, Timbre.SYNTH), Note(A5, 200, Timbre.SYNTH),
                Note(G5, 220, Timbre.SYNTH), Note(E5, 220, Timbre.SYNTH), Note(D5, 200, Timbre.SYNTH), Note(E5, 350, Timbre.SYNTH)
            )
        }
    }

    private fun generateStereoNoteSamples(freq: Double, durationMs: Int, timbre: Timbre, isAccentBeat: Boolean): ByteArray {
        val numFrames = (SAMPLE_RATE * (durationMs / 1000.0)).toInt().coerceAtLeast(100)
        // 2 channels * 2 bytes per sample = 4 bytes per frame
        val sampleByteArray = ByteArray(numFrames * 4)

        val attackFrames = (numFrames * 0.08).toInt().coerceAtLeast(1)
        val decayFrames = (numFrames * 0.18).toInt().coerceAtLeast(1)
        val drumKickFrames = (numFrames * 0.25).toInt().coerceAtLeast(10)

        val bassFreq = if (freq > 0) freq / 2.0 else 0.0

        for (i in 0 until numFrames) {
            val t = i.toDouble() / SAMPLE_RATE

            // 1. Envelope (Attack - Sustain - Release)
            val envelope = when {
                i < attackFrames -> i.toDouble() / attackFrames
                i > (numFrames - decayFrames) -> (numFrames - i).toDouble() / decayFrames
                else -> 1.0
            }

            // 2. Lead Instrument Melody Harmonic Synthesis
            val melodyVal = if (freq <= 0.0) 0.0 else {
                when (timbre) {
                    Timbre.ACCORDION -> {
                        // Rich accordion chorus triads
                        0.45 * sin(2.0 * PI * freq * t) +
                        0.30 * sin(2.0 * PI * (freq * 1.004) * t) +
                        0.25 * sin(2.0 * PI * (freq * 2) * t) +
                        0.15 * sin(2.0 * PI * (freq * 3) * t)
                    }
                    Timbre.BRASS -> {
                        // Powerful cinematic brass trumpet
                        0.40 * sin(2.0 * PI * freq * t) +
                        0.30 * sin(2.0 * PI * (freq * 2) * t) +
                        0.25 * sin(2.0 * PI * (freq * 3) * t) +
                        0.15 * sin(2.0 * PI * (freq * 4) * t)
                    }
                    Timbre.FLUTE -> {
                        // Expressive sweet flute with vibrato
                        val vibrato = 1.0 + 0.03 * sin(2.0 * PI * 6.0 * t)
                        (0.70 * sin(2.0 * PI * freq * vibrato * t) +
                         0.25 * sin(2.0 * PI * (freq * 2) * vibrato * t))
                    }
                    Timbre.FOLK -> {
                        // Piercing South Indian Nadaswaram folk tone
                        0.35 * sin(2.0 * PI * freq * t) +
                        0.30 * sin(2.0 * PI * (freq * 2) * t) +
                        0.20 * sin(2.0 * PI * (freq * 3) * t) +
                        0.15 * sin(2.0 * PI * (freq * 5) * t)
                    }
                    Timbre.SYNTH -> {
                        // Punchy 80s Raja synth lead
                        0.55 * sin(2.0 * PI * freq * t) +
                        0.30 * sin(2.0 * PI * (freq * 1.5) * t) +
                        0.15 * sin(2.0 * PI * (freq * 2) * t)
                    }
                }
            }

            // 3. Warm Bassline Layer (Octave lower)
            val bassVal = if (bassFreq <= 0.0) 0.0 else {
                0.35 * sin(2.0 * PI * bassFreq * t) + 0.15 * sin(2.0 * PI * (bassFreq * 2) * t)
            }

            // 4. Tamil Rhythm Drum Beat Hit (Kick & Snare Click at start of note)
            val drumKick = if (i < drumKickFrames) {
                val kickEnv = (drumKickFrames - i).toDouble() / drumKickFrames
                val kickFreq = 140.0 * kickEnv + 40.0
                val kickVol = if (isAccentBeat) 0.8 else 0.4
                kickVol * sin(2.0 * PI * kickFreq * t) * kickEnv
            } else 0.0

            // High-hat click at beat start
            val hiHatClick = if (i < 80) {
                (Random.nextDouble(-0.15, 0.15) * (80 - i) / 80)
            } else 0.0

            // Combine Layers
            val combinedAudioLeft = ((melodyVal * 0.6 + bassVal * 0.3 + drumKick * 0.4 + hiHatClick) * envelope * 28000.0)
            val combinedAudioRight = ((melodyVal * 0.6 + bassVal * 0.3 + drumKick * 0.4 - hiHatClick) * envelope * 28000.0)

            val sampleLeft = combinedAudioLeft.toInt().coerceIn(-32768, 32767)
            val sampleRight = combinedAudioRight.toInt().coerceIn(-32768, 32767)

            val byteIdx = i * 4
            // Left channel (Little Endian)
            sampleByteArray[byteIdx] = (sampleLeft and 0x00FF).toByte()
            sampleByteArray[byteIdx + 1] = ((sampleLeft shr 8) and 0x00FF).toByte()

            // Right channel (Little Endian)
            sampleByteArray[byteIdx + 2] = (sampleRight and 0x00FF).toByte()
            sampleByteArray[byteIdx + 3] = ((sampleRight shr 8) and 0x00FF).toByte()
        }

        return sampleByteArray
    }
}

