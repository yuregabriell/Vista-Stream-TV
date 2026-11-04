package com.ivor.openstream.data.streaming

import com.ivor.openstream.data.settings.SourceSearchMode

/** First-pass inclusion policy independent of provider transport or title format. */
internal object SourceSearchPolicy {
    /** Automatic TV scans stop as soon as a provider returns; manual Find more does not. */
    fun firstPlayableOnlyOnTv(isTv: Boolean, preferLastWorking: Boolean, expanded: Boolean): Boolean =
        isTv && !expanded

    fun includeAllProviders(mode: SourceSearchMode, expanded: Boolean): Boolean =
        mode == SourceSearchMode.ALL_AT_ONCE || expanded
}
