package net.sfelabs.knox.core.feature.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.feature.api.ActionPolicyState
import net.sfelabs.knox.core.feature.api.PolicyCapability
import net.sfelabs.knox.core.feature.api.PolicyCategory
import net.sfelabs.knox.core.feature.api.PolicyComponent
import net.sfelabs.knox.core.feature.api.PolicyKey
import net.sfelabs.knox.core.feature.api.PolicyUiConverter
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler
import net.sfelabs.knox.core.feature.ui.model.ConfigurationOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A NotSupported result from a set must evict the cached entry. An [ActionPolicyState] only
 * learns that its API is missing by running, so a stale "supported" entry would otherwise be
 * served forever.
 */
class CachedPolicyRegistryNotSupportedTest {

    private val testKey = object : PolicyKey<ActionPolicyState> {
        override val policyName = "reset_action"
    }

    private val testUiConverter = object : PolicyUiConverter<ActionPolicyState> {
        override fun fromUiState(uiEnabled: Boolean, options: List<ConfigurationOption>) = ActionPolicyState()
        override fun getConfigurationOptions(state: ActionPolicyState): List<ConfigurationOption> = emptyList()
    }

    private lateinit var handler: PolicyHandler<ActionPolicyState>
    private lateinit var registry: CachedPolicyRegistry

    @Before
    fun setUp() {
        handler = mockk()
        val component = object : PolicyComponent<ActionPolicyState> {
            override val policyName = testKey.policyName
            override val title = "Reset"
            override val description = "Resets things"
            override val category = PolicyCategory.Action
            override val handler = this@CachedPolicyRegistryNotSupportedTest.handler
            override val defaultValue = ActionPolicyState()
            override val key = testKey
            override val uiConverter = testUiConverter
            override val capabilities = emptySet<PolicyCapability>()
        }
        registry = CachedPolicyRegistry(DefaultPolicyRegistry()).apply {
            components = setOf(component)
        }
    }

    @Test
    fun `setAndRefreshPolicyState NotSupported evicts the cached entry`() = runTest {
        coEvery { handler.getState(any()) } returnsMany listOf(
            ActionPolicyState(),                      // initial load: looks supported
            ActionPolicyState(isSupported = false)    // after the failed run
        )
        coEvery { handler.setState(any()) } returns ApiResult.NotSupported

        // Prime the cache
        val primed = registry.getPolicyState(testKey.policyName)!!
        assertTrue(primed.state.isSupported)

        val result = registry.setAndRefreshPolicyState(testKey, ActionPolicyState())
        assertTrue(result is ApiResult.NotSupported)

        // Must re-read rather than serve the primed entry
        val refreshed = registry.getPolicyState(testKey.policyName)!!
        assertEquals(false, refreshed.state.isSupported)
        coVerify(exactly = 2) { handler.getState(any()) }
    }

    @Test
    fun `setPolicyState NotSupported evicts the cached entry`() = runTest {
        coEvery { handler.getState(any()) } returnsMany listOf(
            ActionPolicyState(),
            ActionPolicyState(isSupported = false)
        )
        coEvery { handler.setState(any()) } returns ApiResult.NotSupported

        registry.getPolicyState(testKey.policyName)
        val result = registry.setPolicyState(testKey, ActionPolicyState())
        assertTrue(result is ApiResult.NotSupported)

        val refreshed = registry.getPolicyState(testKey.policyName)!!
        assertEquals(false, refreshed.state.isSupported)
        coVerify(exactly = 2) { handler.getState(any()) }
    }

    @Test
    fun `setPolicyState Error keeps the cached entry`() = runTest {
        coEvery { handler.getState(any()) } returns ActionPolicyState()
        coEvery { handler.setState(any()) } returns ApiResult.Error(
            net.sfelabs.knox.core.domain.usecase.model.DefaultApiError.UnexpectedError("boom")
        )

        registry.getPolicyState(testKey.policyName)
        registry.setPolicyState(testKey, ActionPolicyState())
        registry.getPolicyState(testKey.policyName)

        // An Error is transient; the existing behaviour of serving the cached entry is kept
        coVerify(exactly = 1) { handler.getState(any()) }
    }
}
