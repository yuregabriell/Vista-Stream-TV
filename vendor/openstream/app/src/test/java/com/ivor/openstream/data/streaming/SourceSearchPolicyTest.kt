package com.ivor.openstream.data.streaming

import com.ivor.openstream.data.settings.SourceSearchMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSearchPolicyTest {
    @Test fun initialParallelSearchIncludesTorrentsAndBackups() {
        assertTrue(SourceSearchPolicy.includeAllProviders(
            SourceSearchMode.ALL_AT_ONCE, expanded = false
        ))
    }

    @Test fun orderedModePreservesExplicitEarlyStop() {
        assertFalse(SourceSearchPolicy.includeAllProviders(
            SourceSearchMode.IN_ORDER, expanded = false
        ))
    }

    @Test fun explicitRefreshIncludesEveryProvider() {
        assertTrue(SourceSearchPolicy.includeAllProviders(
            SourceSearchMode.IN_ORDER, expanded = true
        ))
    }
}
