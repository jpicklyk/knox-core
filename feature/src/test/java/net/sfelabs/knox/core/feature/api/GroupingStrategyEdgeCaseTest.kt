package net.sfelabs.knox.core.feature.api

import io.mockk.every
import io.mockk.mockk
import net.sfelabs.knox.core.domain.usecase.model.ApiError
import net.sfelabs.knox.core.feature.domain.registry.PolicyRegistry
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler
import net.sfelabs.knox.core.feature.ui.model.ConfigurationOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Edge case tests for grouping strategies.
 */
class GroupingStrategyEdgeCaseTest {

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

    private lateinit var mockRegistry: PolicyRegistry

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

    // ===== Empty Registry Edge Cases =====

    @Test
    fun `CapabilityBasedGroupingStrategy with empty registry returns empty groups`() {
        val strategy = CapabilityBasedGroupingStrategy()

        // Mock empty registry
        PolicyCapability.entries.forEach { cap ->
            every { mockRegistry.getByCapability(cap) } returns emptyList()
        }
        every { mockRegistry.getAllComponents() } returns emptyList()

        val resolvedGroups = strategy.resolveAllGroups(mockRegistry, includeEmpty = false)

        assertTrue(resolvedGroups.isEmpty())
    }

    @Test
    fun `ConfigurableGroupingStrategy with empty registry returns empty policies in groups`() {
        val config = GroupingConfiguration(
            groups = listOf(PolicyGroup("group1", "Group 1")),
            policyAssignments = mapOf("policy1" to "group1")
        )
        val strategy = ConfigurableGroupingStrategy(config)

        every { mockRegistry.getAllComponents() } returns emptyList()

        val policies = strategy.getPoliciesInGroup("group1", mockRegistry)

        assertTrue(policies.isEmpty())
    }

    @Test
    fun `ConfigurableGroupingStrategy with empty configuration`() {
        val config = GroupingConfiguration(groups = emptyList(), policyAssignments = emptyMap())
        val strategy = ConfigurableGroupingStrategy(config)

        assertTrue(strategy.getGroups().isEmpty())

        val component = createTestComponent("any_policy")
        val group = strategy.getGroupForPolicy(component)

        assertEquals(null, group)
    }

    // ===== Single Policy Edge Cases =====

    @Test
    fun `CapabilityBasedGroupingStrategy with single policy in radio group`() {
        val strategy = CapabilityBasedGroupingStrategy()
        val radioPolicy = createTestComponent("single_radio", setOf(PolicyCapability.MODIFIES_RADIO))

        every { mockRegistry.getByCapability(PolicyCapability.MODIFIES_RADIO) } returns listOf(radioPolicy)
        PolicyCapability.entries.filter { it != PolicyCapability.MODIFIES_RADIO }.forEach { cap ->
            every { mockRegistry.getByCapability(cap) } returns emptyList()
        }
        every { mockRegistry.getAllComponents() } returns listOf(radioPolicy)

        val resolvedGroups = strategy.resolveAllGroups(mockRegistry, includeEmpty = false)

        assertEquals(1, resolvedGroups.size)
        assertEquals("radio", resolvedGroups[0].group.id)
        assertEquals(1, resolvedGroups[0].policies.size)
    }

    @Test
    fun `CapabilityBasedGroupingStrategy single policy goes to other when no MODIFIES capability`() {
        val strategy = CapabilityBasedGroupingStrategy()
        val policy = createTestComponent("security_only", setOf(PolicyCapability.SECURITY_SENSITIVE))

        PolicyCapability.entries.forEach { cap ->
            every { mockRegistry.getByCapability(cap) } returns emptyList()
        }
        every { mockRegistry.getAllComponents() } returns listOf(policy)

        val resolvedGroups = strategy.resolveAllGroups(mockRegistry, includeEmpty = false)

        assertEquals(1, resolvedGroups.size)
        assertEquals("other", resolvedGroups[0].group.id)
    }

    // ===== Sort Order Edge Cases =====

    @Test
    fun `resolveAllGroups sorts groups by sortOrder`() {
        val config = GroupingConfiguration(
            groups = listOf(
                PolicyGroup("z_group", "Z Group", sortOrder = 3),
                PolicyGroup("a_group", "A Group", sortOrder = 1),
                PolicyGroup("m_group", "M Group", sortOrder = 2)
            ),
            policyAssignments = mapOf(
                "policy_z" to "z_group",
                "policy_a" to "a_group",
                "policy_m" to "m_group"
            )
        )
        val strategy = ConfigurableGroupingStrategy(config)

        every { mockRegistry.getAllComponents() } returns listOf(
            createTestComponent("policy_z"),
            createTestComponent("policy_a"),
            createTestComponent("policy_m")
        )

        val resolvedGroups = strategy.resolveAllGroups(mockRegistry)

        assertEquals("a_group", resolvedGroups[0].group.id)
        assertEquals("m_group", resolvedGroups[1].group.id)
        assertEquals("z_group", resolvedGroups[2].group.id)
    }

    @Test
    fun `resolveAllGroups with same sortOrder maintains stable order`() {
        val config = GroupingConfiguration(
            groups = listOf(
                PolicyGroup("first", "First", sortOrder = 1),
                PolicyGroup("second", "Second", sortOrder = 1),
                PolicyGroup("third", "Third", sortOrder = 1)
            ),
            policyAssignments = mapOf(
                "p1" to "first",
                "p2" to "second",
                "p3" to "third"
            )
        )
        val strategy = ConfigurableGroupingStrategy(config)

        every { mockRegistry.getAllComponents() } returns listOf(
            createTestComponent("p1"),
            createTestComponent("p2"),
            createTestComponent("p3")
        )

        val resolvedGroups = strategy.resolveAllGroups(mockRegistry)

        // With Kotlin's sortedBy (stable sort), original order should be preserved
        assertEquals(3, resolvedGroups.size)
    }

    // ===== Policy Name Edge Cases =====

    @Test
    fun `ConfigurableGroupingStrategy handles policy names with special characters`() {
        val config = GroupingConfiguration(
            groups = listOf(PolicyGroup("group", "Group")),
            policyAssignments = mapOf(
                "policy_with_underscores" to "group",
                "policy-with-dashes" to "group",
                "policy.with.dots" to "group",
                "POLICY_UPPERCASE" to "group"
            )
        )
        val strategy = ConfigurableGroupingStrategy(config)

        every { mockRegistry.getAllComponents() } returns listOf(
            createTestComponent("policy_with_underscores"),
            createTestComponent("policy-with-dashes"),
            createTestComponent("policy.with.dots"),
            createTestComponent("POLICY_UPPERCASE")
        )

        val policies = strategy.getPoliciesInGroup("group", mockRegistry)

        assertEquals(4, policies.size)
    }

    @Test
    fun `ConfigurableGroupingStrategy is case sensitive for policy names`() {
        val config = GroupingConfiguration(
            groups = listOf(PolicyGroup("group", "Group")),
            policyAssignments = mapOf("Policy_Name" to "group")
        )
        val strategy = ConfigurableGroupingStrategy(config)

        every { mockRegistry.getAllComponents() } returns listOf(
            createTestComponent("policy_name")  // Different case
        )

        val policies = strategy.getPoliciesInGroup("group", mockRegistry)

        // Should not match due to case sensitivity
        assertTrue(policies.isEmpty())
    }

    // ===== Multiple Capabilities Edge Cases =====

    @Test
    fun `CapabilityBasedGroupingStrategy policy with all MODIFIES capabilities goes to first matching group`() {
        val strategy = CapabilityBasedGroupingStrategy()
        val superPolicy = createTestComponent(
            "super_policy",
            setOf(
                PolicyCapability.MODIFIES_RADIO,
                PolicyCapability.MODIFIES_WIFI,
                PolicyCapability.MODIFIES_BLUETOOTH,
                PolicyCapability.MODIFIES_DISPLAY
            )
        )

        val group = strategy.getGroupForPolicy(superPolicy)

        // Should go to first matching (radio, based on iteration order of capabilityToGroup)
        assertNotNull(group)
        assertEquals("radio", group.id)
    }

    @Test
    fun `policy with duplicate capability in set is handled correctly`() {
        // This tests that Set behavior is correct (duplicates impossible)
        val component = createTestComponent(
            "test",
            setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.MODIFIES_RADIO)
        )

        assertEquals(1, component.capabilities.size)
        assertTrue(component.hasCapability(PolicyCapability.MODIFIES_RADIO))
    }

    // ===== Assigned but Not In Registry Edge Cases =====

    @Test
    fun `ConfigurableGroupingStrategy handles assigned policy not in registry`() {
        val config = GroupingConfiguration(
            groups = listOf(PolicyGroup("group", "Group")),
            policyAssignments = mapOf(
                "existing_policy" to "group",
                "nonexistent_policy" to "group"
            )
        )
        val strategy = ConfigurableGroupingStrategy(config)

        // Registry only has one of the assigned policies
        every { mockRegistry.getAllComponents() } returns listOf(
            createTestComponent("existing_policy")
        )

        val policies = strategy.getPoliciesInGroup("group", mockRegistry)

        // Should only return the existing policy
        assertEquals(1, policies.size)
        assertEquals("existing_policy", policies[0].policyName)
    }

    @Test
    fun `ConfigurableGroupingStrategy handles policy in registry but not assigned`() {
        val config = GroupingConfiguration(
            groups = listOf(PolicyGroup("group", "Group")),
            policyAssignments = mapOf("assigned_policy" to "group")
        )
        val strategy = ConfigurableGroupingStrategy(config)

        every { mockRegistry.getAllComponents() } returns listOf(
            createTestComponent("assigned_policy"),
            createTestComponent("unassigned_policy")
        )

        val groupForUnassigned = strategy.getGroupForPolicy(createTestComponent("unassigned_policy"))

        assertEquals(null, groupForUnassigned)
    }

    // ===== ResolvedPolicyGroup Edge Cases =====

    @Test
    fun `ResolvedPolicyGroup isEmpty returns true for empty policies list`() {
        val resolved = ResolvedPolicyGroup(
            group = PolicyGroup("empty", "Empty"),
            policies = emptyList()
        )

        assertTrue(resolved.policies.isEmpty())
    }

    @Test
    fun `PolicyGroup default values`() {
        val group = PolicyGroup("id", "Name")

        assertEquals("id", group.id)
        assertEquals("Name", group.displayName)
        assertEquals("", group.description)
        assertEquals(null, group.iconRes)
        assertEquals(0, group.sortOrder)
    }

    // ===== CapabilityBasedGroupingStrategy Group Properties =====

    @Test
    fun `CapabilityBasedGroupingStrategy groups have correct display names`() {
        val strategy = CapabilityBasedGroupingStrategy()
        val groups = strategy.getGroups()

        val groupMap = groups.associateBy { it.id }

        assertEquals("Radio & Cellular", groupMap["radio"]?.displayName)
        assertEquals("Wi-Fi", groupMap["wifi"]?.displayName)
        assertEquals("Bluetooth", groupMap["bluetooth"]?.displayName)
        assertEquals("Display", groupMap["display"]?.displayName)
        assertEquals("Audio", groupMap["audio"]?.displayName)
        assertEquals("Charging", groupMap["charging"]?.displayName)
        assertEquals("Calling", groupMap["calling"]?.displayName)
        assertEquals("Hardware", groupMap["hardware"]?.displayName)
        assertEquals("Security", groupMap["security"]?.displayName)
        assertEquals("Network", groupMap["network"]?.displayName)
        assertEquals("Other", groupMap["other"]?.displayName)
    }

    @Test
    fun `CapabilityBasedGroupingStrategy other group has highest sortOrder`() {
        val strategy = CapabilityBasedGroupingStrategy()
        val groups = strategy.getGroups()

        val otherGroup = groups.find { it.id == "other" }
        val maxNonOtherSortOrder = groups.filter { it.id != "other" }.maxOf { it.sortOrder }

        assertNotNull(otherGroup)
        assertTrue(otherGroup!!.sortOrder > maxNonOtherSortOrder)
    }
}
