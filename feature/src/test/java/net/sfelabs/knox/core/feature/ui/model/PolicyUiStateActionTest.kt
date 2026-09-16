package net.sfelabs.knox.core.feature.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyUiStateActionTest {

    private val action = PolicyUiState.Action(
        isSupported = true,
        title = "Reset All Settings",
        policyName = "reset_all_settings",
        description = "Resets every Knox custom setting",
        isLoading = false,
        error = "Previous error",
        lastRunSucceeded = true
    )

    @Test
    fun `Action is never enabled and has no options`() {
        assertFalse(action.isEnabled)
        assertTrue(action.currentOptions().isEmpty())
    }

    @Test
    fun `Action copyWithLoading clears error and preserves last run`() {
        val loading = action.copyWithLoading(isLoading = true) as PolicyUiState.Action

        assertNull(loading.error)
        assertTrue(loading.isLoading)
        assertEquals(true, loading.lastRunSucceeded)
        assertEquals(action.title, loading.title)
        assertEquals(action.policyName, loading.policyName)
    }

    @Test
    fun `Action copyWithError sets error and clears loading`() {
        val failed = action.copyWithLoading(isLoading = true).copyWithError("New error") as PolicyUiState.Action

        assertEquals("New error", failed.error)
        assertFalse(failed.isLoading)
        assertEquals(true, failed.lastRunSucceeded)
    }
}
