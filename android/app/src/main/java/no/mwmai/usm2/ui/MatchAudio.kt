package no.mwmai.usm2.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import no.mwmai.usm2.R

/**
 * The match crowd SFX, faithful to the original game's own samples (res/raw OGGs
 * transcoded from the real WAVs by tools/stage_audio.py). A thin [SoundPool]
 * wrapper: a short ref whistle to start and restart, the long whistle for full
 * time, a roar/cheer when the managed side scores, a jeer when it concedes, and
 * the long initial cheer as the crowd welcome at kick-off.
 *
 * The welcome is gated on load completion (it is the largest sample, so it is the
 * last to decode); the others fire seconds into the match, by which point they
 * are loaded, so they play directly.
 */
class MatchAudio(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val whistle = pool.load(context, R.raw.r_whistle, 1)
    private val fulltime = pool.load(context, R.raw.r_fulltime, 1)
    private val roar = pool.load(context, R.raw.crowd_roar, 1)
    private val cheer = pool.load(context, R.raw.crowd_cheer, 1)
    private val jeer = pool.load(context, R.raw.crowd_jeer, 1)
    private val welcome = pool.load(context, R.raw.crowd_welcome, 1)

    private val loaded = HashSet<Int>()
    private var welcomePending = false

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) {
                loaded.add(id)
                if (welcomePending && id == welcome) {
                    welcomePending = false
                    play(welcome, 0.7f)
                }
            }
        }
    }

    private fun play(id: Int, vol: Float) {
        if (id != 0) pool.play(id, vol, vol, 1, 0, 1f)
    }

    /** Crowd welcome at kick-off; deferred until the sample has decoded. */
    fun welcome() {
        if (welcome in loaded) play(welcome, 0.7f) else welcomePending = true
    }

    fun whistle() = play(whistle, 0.9f)
    fun fullTime() = play(fulltime, 0.95f)

    /** [goalIndex] alternates roar/cheer so repeated goals don't sound identical. */
    fun goalFor(goalIndex: Int) = play(if (goalIndex % 2 == 0) roar else cheer, 1f)
    fun goalAgainst() = play(jeer, 0.9f)

    fun release() = pool.release()
}

/** A [MatchAudio] scoped to the composition; released when the match view leaves. */
@Composable
fun rememberMatchAudio(): MatchAudio {
    val context = LocalContext.current
    val audio = remember { MatchAudio(context) }
    DisposableEffect(Unit) { onDispose { audio.release() } }
    return audio
}
