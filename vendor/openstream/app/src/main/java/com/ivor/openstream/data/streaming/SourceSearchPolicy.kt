package com.ivor.openstream.data.streaming

import com.ivor.openstream.data.settings.SourceSearchMode

/** First-pass inclusion policy independent of provider transport or title format. */
internal object SourceSearchPolicy {
    fun includeAllProviders(mode: SourceSearchMode, expanded: Boolean): Boolean =
        mode == SourceSearchMode.ALL_AT_ONCE || expanded
}
