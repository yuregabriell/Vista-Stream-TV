package com.ivor.openstream.data.streaming

import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TV playback owns the network/decoder budget. Once a video is ready, cancel
 * provider discovery jobs that were deliberately detached from a search flow
 * to bound blocking addon work. The currently playing Media3/P2P stream is not
 * part of these jobs and must never be closed by the guard.
 */
internal object TvPlaybackSearchGuard {
    private val playing = AtomicBoolean(false)
    private val providerJobs = ConcurrentHashMap.newKeySet<Job>()

    fun isPlaying(): Boolean = playing.get()

    fun beginPlayback() {
        playing.set(true)
        providerJobs.toList().forEach { it.cancel() }
    }

    fun endPlayback() {
        playing.set(false)
    }

    fun track(job: Job) {
        if (playing.get()) {
            job.cancel()
            return
        }
        providerJobs.add(job)
        if (playing.get()) {
            providerJobs.remove(job)
            job.cancel()
        }
    }

    fun untrack(job: Job) {
        providerJobs.remove(job)
    }
}
