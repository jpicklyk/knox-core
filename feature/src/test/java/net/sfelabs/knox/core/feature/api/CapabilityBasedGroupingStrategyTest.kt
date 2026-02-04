package net.sfelabs.knox.core.feature.api

import io.mockk.every
import io.mockk.mockk
import net.sfelabs.knox.core.domain.usecase.model.ApiError
import net.sfelabs.knox.core.feature.domain.registry.PolicyRegistry
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler
import net.sfelabs.knox.core.feature.ui.model.ConfigurationOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CapabilityBasedGroupingStrategyTest {

    private lateinit var strategy: CapabilityBasedGroupingStrategy
    private lateinit var mockRegistry: PolicyRegistry

    // Test PolicyState implementation
    private data class TestState(
        override val isEnabled: Boolean,
        override val isSupported: Boolean = true,
        override val error: ApiError? = null,
        override val exception: Throwable? = null
    ) : PolicyState {
        override fun withEnabled(enabled: Boolean) = copy(isEnabled = enabled)
        override fun withError(error: ApiError?, exception: Throwable?) = copy(error = error, exception = exception)
    }

    private val testUiConverter = object : PolicyUiConverter<TestState> {
        override fun fromUiState(uiEnabled: Boolean, options: List<ConfigurationOption>): TestState {
            return TestState(isEnabled = uiEnabled)
        }
        override fun getConfigurationOptions(state: TestState): List<ConfigurationOption> = emptyList()
    }

    private fun createTestComponent(
        name: String,
        capabilities: Set<PolicyCapability>
    ): PolicyComponent<TestState> {
        return object : PolicyComponent<TestState> {
            override val policyName = name
            override val title = "Test $name"
            override val description = "Test description"
            override val category = PolicyCategory.Toggle
            override val handler = mockk<PolicyHandler<TestState>>()
            override val defaultValue = TestState(isEnabled = false)
            override val key = object : PolicyKey<TestState> {
                override val policyName = name
            }
            override val uiConverter = testUiConverter
            override val capabilities = capabilities
        }
    }

    @Before
    fun setup() {
        strategy = CapabilityBasedGroupingStrategy()
        mockRegistry = mockk()
    }

    @Test
    fun `getGroups returns all capability-based groups plus other`() {
        val groups = strategy.getGroups()

        // Should have groups for all MODIFIES_* capabilities plus "other"
        assertTrue(groups.any { it.id == "radio" })
        assertTrue(groups.any { it.id == "wifi" })
        assertTrue(groups.any { it.id == "bluetooth" })
        assertTrue(groups.any { it.id == "display" })
        assertTrue(groups.any { it.id == "audio" })
        assertTrue(groups.any { it.id == "charging" })
        assertTrue(groups.any { it.id == "calling" })
        assertTrue(groups.any { it.id == "hardware" })
        assertTrue(groups.any { it.id == "security" })
        assertTrue(groups.any { it.id == "network" })
        assertTrue(groups.any { it.id == "other" })
    }

    @Test
    fun `getGroups returns groups sorted by sortOrder`() {
        val groups = strategy.getGroups()

        // Verify groups are in expected order
        val sortedGroups = groups.sortedBy { it.sortOrder }
        assertEquals(groups, sortedGroups)

        // "other" should be last (highest sortOrder)
        assertEquals("other", groups.last().id)
    }

    @Test
    fun `getGroupForPolicy returns correct group for policy with MODIFIES_RADIO`() {
        val component = createTestComponent(
            "band_locking_5g",
            setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.REQUIRES_SIM)
        )

        val group = strategy.getGroupForPolicy(component)

        assertEquals("radio", group.id)
        assertEquals("Radio & Cellular", group.displayName)
    }

    @Test
    fun `getGroupForPolicy returns correct group for policy with MODIFIES_DISPLAY`() {
        val component = createTestComponent(
            "night_vision_mode",
            setOf(PolicyCapability.MODIFIES_DISPLAY)
        )

        val group = strategy.getGroupForPolicy(component)

        assertEquals("display", group.id)
        assertEquals("Display", group.displayName)
    }

    @Test
    fun `getGroupForPolicy returns first matching group when policy has multiple MODIFIES capabilities`() {
        // Policy with both radio and network capabilities
        val component = createTestComponent(
            "multi_capability_policy",
            setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.MODIFIES_NETWORK)
        )

        val group = strategy.getGroupForPolicy(component)

        // Should return first matching (MODIFIES_RADIO comes before MODIFIES_NETWORK in iteration order)
        assertEquals("radio", group.id)
    }

    @Test
    fun `getGroupForPolicy returns other for policy without MODIFIES capabilities`() {
        val component = createTestComponent(
            "special_policy",
            setOf(PolicyCapability.SECURITY_SENSITIVE, PolicyCapability.REQUIRES_REBOOT)
        )

        val group = strategy.getGroupForPolicy(component)

        assertEquals("other", group.id)
        assertEquals("Other", group.displayName)
    }

    @Test
    fun `getGroupForPolicy returns other for policy with no capabilities`() {
        val component = createTestComponent(
            "basic_policy",
            emptySet()
        )

        val group = strategy.getGroupForPolicy(component)

        assertEquals("other", group.id)
    }

    @Test
    fun `getPoliciesInGroup returns policies with matching capability from registry`() {
        val radioPolicy1 = createTestComponent(
            "band_locking_5g",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )
        val radioPolicy2 = createTestComponent(
            "disable_2g",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )

        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_RADIO) } returns listOf(radioPolicy1, radioPolicy2)

        val policies = strategy.getPoliciesInGroup("radio", mockRegistry)

        assertEquals(2, policies.size)
        assertTrue(policies.any { it.policyName == "band_locking_5g" })
        assertTrue(policies.any { it.policyName == "disable_2g" })
    }

    @Test
    fun `getPoliciesInGroup returns policies without MODIFIES capabilities for other group`() {
        val otherPolicy = createTestComponent(
            "special_policy",
            setOf(PolicyCapability.SECURITY_SENSITIVE)
        )
        val radioPolicy = createTestComponent(
            "band_locking_5g",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )

        every { mockRegistry.getAllComponents() } returns listOf(otherPolicy, radioPolicy)

        val policies = strategy.getPoliciesInGroup("other", mockRegistry)

        assertEquals(1, policies.size)
        assertEquals("special_policy", policies[0].policyName)
    }

    @Test
    fun `getPoliciesInGroup returns empty list for unknown group id`() {
        val policies = strategy.getPoliciesInGroup("unknown_group", mockRegistry)

        assertTrue(policies.isEmpty())
    }

    @Test
    fun `resolveAllGroups returns groups with their policies`() {
        val radioPolicy = createTestComponent(
            "band_locking_5g",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )
        val displayPolicy = createTestComponent(
            "night_vision",
            setOf(PolicyCapability.MODIFIES_DISPLAY)
        )
        val otherPolicy = createTestComponent(
            "special",
            setOf(PolicyCapability.SECURITY_SENSITIVE)
        )

        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_RADIO) } returns listOf(radioPolicy)
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_WIFI) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_BLUETOOTH) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_DISPLAY) } returns listOf(displayPolicy)
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_AUDIO) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_CHARGING) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_CALLING) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_HARDWARE) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_BROWSER) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_SECURITY) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_NETWORK) } returns emptyList()
        every { mockRegistry.getAllComponents() } returns listOf(radioPolicy, displayPolicy, otherPolicy)

        val resolvedGroups = strategy.resolveAllGroups(mockRegistry)

        // Find specific groups
        val radioGroup = resolvedGroups.find { it.group.id == "radio" }
        val displayGroup = resolvedGroups.find { it.group.id == "display" }
        val otherGroup = resolvedGroups.find { it.group.id == "other" }

        assertEquals(1, radioGroup?.policies?.size)
        assertEquals("band_locking_5g", radioGroup?.policies?.get(0)?.policyName)

        assertEquals(1, displayGroup?.policies?.size)
        assertEquals("night_vision", displayGroup?.policies?.get(0)?.policyName)

        assertEquals(1, otherGroup?.policies?.size)
        assertEquals("special", otherGroup?.policies?.get(0)?.policyName)
    }

    @Test
    fun `resolveAllGroups excludes empty groups when includeEmpty is false`() {
        val radioPolicy = createTestComponent(
            "band_locking_5g",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )

        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_RADIO) } returns listOf(radioPolicy)
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_WIFI) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_BLUETOOTH) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_DISPLAY) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_AUDIO) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_CHARGING) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_CALLING) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_HARDWARE) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_BROWSER) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_SECURITY) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_NETWORK) } returns emptyList()
        every { mockRegistry.getAllComponents() } returns listOf(radioPolicy)

        val resolvedGroups = strategy.resolveAllGroups(mockRegistry, includeEmpty = false)

        // Only radio group should be present
        assertEquals(1, resolvedGroups.size)
        assertEquals("radio", resolvedGroups[0].group.id)
    }

    @Test
    fun `resolveAllGroups includes empty groups when includeEmpty is true`() {
        val radioPolicy = createTestComponent(
            "band_locking_5g",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )

        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_RADIO) } returns listOf(radioPolicy)
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_WIFI) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_BLUETOOTH) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_DISPLAY) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_AUDIO) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_CHARGING) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_CALLING) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_HARDWARE) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_BROWSER) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_SECURITY) } returns emptyList()
        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_NETWORK) } returns emptyList()
        every { mockRegistry.getAllComponents() } returns listOf(radioPolicy)

        val resolvedGroups = strategy.resolveAllGroups(mockRegistry, includeEmpty = true)

        // All groups should be present (12 total)
        assertEquals(12, resolvedGroups.size)
    }
}
