package com.ivor.openstream.presentation.player

import com.ivor.openstream.domain.model.VideoServer

/**
 * The first usable stream begins immediately. New addon responses are added to
 * the picker but never replace the video already buffering/playing. User choices,
 * failures and explicit retries are handled separately by PlayerViewModel.
 */
internal object PlaybackSourcePolicy {
    fun select(
        eligible: List<VideoServer>,
        userChosenId: String?,
        currentActiveId: String?,
        rememberedId: String?,
        playbackStarted: Boolean
    ): VideoServer? {
        return eligible.firstOrNull { it.id == userChosenId }
            ?: eligible.firstOrNull { it.id == currentActiveId }
            ?: rememberedId.takeUnless { playbackStarted }?.let { id ->
                eligible.firstOrNull { it.id == id }
            }
            ?: eligible.firstOrNull()
    }
}
