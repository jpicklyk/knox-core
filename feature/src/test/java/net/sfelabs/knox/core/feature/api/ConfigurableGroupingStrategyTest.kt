package net.sfelabs.knox.core.feature.api

import io.mockk.every
import io.mockk.mockk
import net.sfelabs.knox.core.domain.usecase.model.ApiError
import net.sfelabs.knox.core.feature.domain.registry.PolicyRegistry
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler
import net.sfelabs.knox.core.feature.ui.model.ConfigurationOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConfigurableGroupingStrategyTest {

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
        capabilities: Set<PolicyCapability> = emptySet()
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
        mockRegistry = mockk()
    }

    @Test
    fun `getGroups returns configured groups`() {
        val config = GroupingConfiguration(
            groups = listOf(
                PolicyGroup("quick", "Quick Access", sortOrder = 1),
                PolicyGroup("advanced", "Advanced", sortOrder = 2)
            ),
            policyAssignments = emptyMap()
        )
        val strategy = ConfigurableGroupingStrategy(config)

        val groups = strategy.getGroups()

        assertEquals(2, groups.size)
        assertEquals("quick", groups[0].id)
        assertEquals("Quick Access", groups[0].displayName)
        assertEquals("advanced", groups[1].id)
        assertEquals("Advanced", groups[1].displayName)
    }

    @Test
    fun `getGroupForPolicy returns correct group for assigned policy`() {
        val config = GroupingConfiguration(
            groups = listOf(
                PolicyGroup("quick", "Quick Access"),
                PolicyGroup("advanced", "Advanced")
            ),
            policyAssignments = mapOf(
                "device_lock_mode" to "quick",
                "band_locking" to "advanced"
            )
        )
        val strategy = ConfigurableGroupingStrategy(config)

        val component = createTestComponent("device_lock_mode")
        val group = strategy.getGroupForPolicy(component)

        assertEquals("quick", group?.id)
    }

    @Test
    fun `getGroupForPolicy returns null for unassigned policy`() {
        val config = GroupingConfiguration(
            groups = listOf(PolicyGroup("quick", "Quick Access")),
            policyAssignments = mapOf("device_lock_mode" to "quick")
        )
        val strategy = ConfigurableGroupingStrategy(config)

        val component = createTestComponent("unknown_policy")
        val group = strategy.getGroupForPolicy(component)

        assertNull(group)
    }

    @Test
    fun `getGroupForPolicy returns null when assigned to non-existent group`() {
        val config = GroupingConfiguration(
            groups = listOf(PolicyGroup("quick", "Quick Access")),
            policyAssignments = mapOf("device_lock_mode" to "nonexistent")
        )
        val strategy = ConfigurableGroupingStrategy(config)

        val component = createTestComponent("device_lock_mode")
        val group = strategy.getGroupForPolicy(component)

        assertNull(group)
    }

    @Test
    fun `getPoliciesInGroup returns policies assigned to that group`() {
        val config = GroupingConfiguration(
            groups = listOf(
                PolicyGroup("quick", "Quick Access"),
                PolicyGroup("advanced", "Advanced")
            ),
            policyAssignments = mapOf(
                "device_lock_mode" to "quick",
                "night_vision" to "quick",
                "band_locking" to "advanced"
            )
        )
        val strategy = ConfigurableGroupingStrategy(config)

        val deviceLockMode = createTestComponent("device_lock_mode")
        val nightVision = createTestComponent("night_vision")
        val bandLocking = createTestComponent("band_locking")
        val unassigned = createTestComponent("unassigned")

        every { mockRegistry.getAllComponents() } returns listOf(
            deviceLockMode, nightVision, bandLocking, unassigned
        )

        val quickPolicies = strategy.getPoliciesInGroup("quick", mockRegistry)

        assertEquals(2, quickPolicies.size)
        assertTrue(quickPolicies.any { it.policyName == "device_lock_mode" })
        assertTrue(quickPolicies.any { it.policyName == "night_vision" })
    }

    @Test
    fun `getPoliciesInGroup returns empty list for unknown group`() {
        val config = GroupingConfiguration(
            groups = listOf(PolicyGroup("quick", "Quick Access")),
            policyAssignments = mapOf("device_lock_mode" to "quick")
        )
        val strategy = ConfigurableGroupingStrategy(config)

        every { mockRegistry.getAllComponents() } returns emptyList()

        val policies = strategy.getPoliciesInGroup("unknown", mockRegistry)

        assertTrue(policies.isEmpty())
    }

    @Test
    fun `getPoliciesInGroup returns empty list for group with no assignments`() {
        val config = GroupingConfiguration(
            groups = listOf(
                PolicyGroup("quick", "Quick Access"),
                PolicyGroup("empty", "Empty Group")
            ),
            policyAssignments = mapOf("device_lock_mode" to "quick")
        )
        val strategy = ConfigurableGroupingStrategy(config)

        val deviceLockMode = createTestComponent("device_lock_mode")
        every { mockRegistry.getAllComponents() } returns listOf(deviceLockMode)

        val policies = strategy.getPoliciesInGroup("empty", mockRegistry)

        assertTrue(policies.isEmpty())
    }

    @Test
    fun `resolveAllGroups returns all groups with their policies`() {
        val config = GroupingConfiguration(
            groups = listOf(
                PolicyGroup("quick", "Quick Access", sortOrder = 1),
                PolicyGroup("advanced", "Advanced", sortOrder = 2)
            ),
            policyAssignments = mapOf(
                "device_lock_mode" to "quick",
                "band_locking" to "advanced"
            )
        )
        val strategy = ConfigurableGroupingStrategy(config)

        val deviceLockMode = createTestComponent("device_lock_mode")
        val bandLocking = createTestComponent("band_locking")
        every { mockRegistry.getAllComponents() } returns listOf(deviceLockMode, bandLocking)

        val resolvedGroups = strategy.resolveAllGroups(mockRegistry)

        assertEquals(2, resolvedGroups.size)

        val quickGroup = resolvedGroups.find { it.group.id == "quick" }
        assertEquals(1, quickGroup?.policies?.size)
        assertEquals("device_lock_mode", quickGroup?.policies?.get(0)?.policyName)

        val advancedGroup = resolvedGroups.find { it.group.id == "advanced" }
        assertEquals(1, advancedGroup?.policies?.size)
        assertEquals("band_locking", advancedGroup?.policies?.get(0)?.policyName)
    }

    // GroupingConfiguration.Builder tests

    @Test
    fun `builder creates configuration with groups and assignments`() {
        val config = GroupingConfiguration.builder()
            .addGroup("quick", "Quick Access", sortOrder = 1)
            .addGroup("advanced", "Advanced", sortOrder = 2)
            .assignPolicy("device_lock_mode", "quick")
            .assignPolicy("band_locking", "advanced")
            .build()

        assertEquals(2, config.groups.size)
        assertEquals("quick", config.policyAssignments["device_lock_mode"])
        assertEquals("advanced", config.policyAssignments["band_locking"])
    }

    @Test
    fun `builder addGroup with PolicyGroup object`() {
        val group = PolicyGroup("custom", "Custom Group", "Custom description", sortOrder = 5)
        val config = GroupingConfiguration.builder()
            .addGroup(group)
            .build()

        assertEquals(1, config.groups.size)
        assertEquals("custom", config.groups[0].id)
        assertEquals("Custom Group", config.groups[0].displayName)
        assertEquals("Custom description", config.groups[0].description)
        assertEquals(5, config.groups[0].sortOrder)
    }

    @Test
    fun `builder assignPolicies assigns multiple policies to group`() {
        val config = GroupingConfiguration.builder()
            .addGroup("quick", "Quick Access")
            .assignPolicies("quick", "policy1", "policy2", "policy3")
            .build()

        assertEquals("quick", config.policyAssignments["policy1"])
        assertEquals("quick", config.policyAssignments["policy2"])
        assertEquals("quick", config.policyAssignments["policy3"])
    }

    @Test
    fun `builder creates empty configuration when nothing added`() {
        val config = GroupingConfiguration.builder().build()

        assertTrue(config.groups.isEmpty())
        assertTrue(config.policyAssignments.isEmpty())
    }

    @Test
    fun `builder allows policy reassignment`() {
        val config = GroupingConfiguration.builder()
            .addGroup("group1", "Group 1")
            .addGroup("group2", "Group 2")
            .assignPolicy("policy", "group1")
            .assignPolicy("policy", "group2")  // Reassign
            .build()

        assertEquals("group2", config.policyAssignments["policy"])
    }

    @Test
    fun `builder addGroup defaults sortOrder to group count`() {
        val config = GroupingConfiguration.builder()
            .addGroup("first", "First")
            .addGroup("second", "Second")
            .addGroup("third", "Third")
            .build()

        assertEquals(0, config.groups[0].sortOrder)
        assertEquals(1, config.groups[1].sortOrder)
        assertEquals(2, config.groups[2].sortOrder)
    }
}
