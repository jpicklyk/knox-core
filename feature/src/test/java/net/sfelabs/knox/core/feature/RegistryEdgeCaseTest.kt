package net.sfelabs.knox.core.feature

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.sfelabs.knox.core.domain.usecase.model.ApiError
import net.sfelabs.knox.core.feature.api.PolicyCapability
import net.sfelabs.knox.core.feature.api.PolicyCategory
import net.sfelabs.knox.core.feature.api.PolicyComponent
import net.sfelabs.knox.core.feature.api.PolicyKey
import net.sfelabs.knox.core.feature.api.PolicyState
import net.sfelabs.knox.core.feature.api.PolicyUiConverter
import net.sfelabs.knox.core.feature.data.repository.DefaultPolicyRegistry
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler
import net.sfelabs.knox.core.feature.ui.model.ConfigurationOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Edge case tests for DefaultPolicyRegistry.
 */
class RegistryEdgeCaseTest {

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

    private lateinit var registry: DefaultPolicyRegistry

    private fun createTestComponent(
        name: String,
        category: PolicyCategory = PolicyCategory.Toggle,
        capabilities: Set<PolicyCapability> = emptySet()
    ): PolicyComponent<TestState> {
        val handler = mockk<PolicyHandler<TestState>>()
        coEvery { handler.getState() } returns TestState(isEnabled = true)

        return object : PolicyComponent<TestState> {
            override val policyName = name
            override val title = "Test $name"
            override val description = "Test description"
            override val category = category
            override val handler = handler
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
        registry = DefaultPolicyRegistry()
    }

    // ===== Empty Registry Edge Cases =====

    @Test
    fun `empty registry returns empty list for getAllComponents`() {
        assertTrue(registry.getAllComponents().isEmpty())
    }

    @Test
    fun `empty registry returns empty list for getAllPolicies`() = runTest {
        assertTrue(registry.getAllPolicies().isEmpty())
    }

    @Test
    fun `empty registry returns empty list for getByCapability`() {
        assertTrue(registry.getByCapability(PolicyCapability.MODIFIES_RADIO).isEmpty())
    }

    @Test
    fun `empty registry returns empty list for getByCategory`() {
        assertTrue(registry.getByCategory(PolicyCategory.Toggle).isEmpty())
    }

    @Test
    fun `empty registry returns empty list for query`() {
        assertTrue(registry.query().isEmpty())
    }

    @Test
    fun `empty registry returns null for getComponent`() {
        val key = object : PolicyKey<TestState> {
            override val policyName = "nonexistent"
        }
        assertNull(registry.getComponent(key))
    }

    @Test
    fun `empty registry returns false for isRegistered`() {
        val key = object : PolicyKey<TestState> {
            override val policyName = "nonexistent"
        }
        assertFalse(registry.isRegistered(key))
    }

    // ===== Setting Components to Empty =====

    @Test
    fun `setting components to empty clears all indexes`() {
        // First add some components
        registry.components = setOf(
            createTestComponent("policy1", capabilities = setOf(PolicyCapability.MODIFIES_RADIO)),
            createTestComponent("policy2", capabilities = setOf(PolicyCapability.MODIFIES_DISPLAY))
        )

        assertEquals(2, registry.getAllComponents().size)
        assertEquals(1, registry.getByCapability(PolicyCapability.MODIFIES_RADIO).size)

        // Now clear
        registry.components = emptySet()

        assertEquals(0, registry.getAllComponents().size)
        assertEquals(0, registry.getByCapability(PolicyCapability.MODIFIES_RADIO).size)
        assertEquals(0, registry.getByCapability(PolicyCapability.MODIFIES_DISPLAY).size)
    }

    // ===== Query with Null vs Empty Capabilities =====

    @Test
    fun `query with null capabilities returns all matching category`() {
        registry.components = setOf(
            createTestComponent("toggle1", PolicyCategory.Toggle),
            createTestComponent("configurable1", PolicyCategory.ConfigurableToggle)
        )

        val result = registry.query(category = PolicyCategory.Toggle, capabilities = null)

        assertEquals(1, result.size)
        assertEquals("toggle1", result[0].policyName)
    }

    @Test
    fun `query with empty capabilities returns all matching category`() {
        registry.components = setOf(
            createTestComponent("toggle1", PolicyCategory.Toggle),
            createTestComponent("configurable1", PolicyCategory.ConfigurableToggle)
        )

        val result = registry.query(category = PolicyCategory.Toggle, capabilities = emptySet())

        assertEquals(1, result.size)
        assertEquals("toggle1", result[0].policyName)
    }

    @Test
    fun `getByCapabilities with empty set returns all components`() {
        registry.components = setOf(
            createTestComponent("policy1"),
            createTestComponent("policy2")
        )

        val result = registry.getByCapabilities(emptySet(), matchAll = false)

        assertEquals(2, result.size)
    }

    // ===== All Capabilities =====

    @Test
    fun `component with all capabilities is found by any single capability query`() {
        val allCapabilities = PolicyCapability.entries.toSet()
        val superComponent = createTestComponent("super_policy", capabilities = allCapabilities)

        registry.components = setOf(superComponent)

        // Should be found by any capability
        PolicyCapability.entries.forEach { cap ->
            val result = registry.getByCapability(cap)
            assertEquals("Should find policy for $cap", 1, result.size)
            assertEquals("super_policy", result[0].policyName)
        }
    }

    @Test
    fun `component with all capabilities passes matchAll for any subset`() {
        val allCapabilities = PolicyCapability.entries.toSet()
        val superComponent = createTestComponent("super_policy", capabilities = allCapabilities)

        registry.components = setOf(superComponent)

        val subset = setOf(
            PolicyCapability.MODIFIES_RADIO,
            PolicyCapability.MODIFIES_WIFI,
            PolicyCapability.SECURITY_SENSITIVE
        )

        val result = registry.getByCapabilities(subset, matchAll = true)

        assertEquals(1, result.size)
    }

    // ===== No Capabilities =====

    @Test
    fun `component with no capabilities is not found by any capability query`() {
        val emptyCapComponent = createTestComponent("empty_cap_policy", capabilities = emptySet())

        registry.components = setOf(emptyCapComponent)

        PolicyCapability.entries.forEach { cap ->
            val result = registry.getByCapability(cap)
            assertTrue("Should not find policy for $cap", result.isEmpty())
        }
    }

    @Test
    fun `component with no capabilities fails matchAll for any non-empty set`() {
        val emptyCapComponent = createTestComponent("empty_cap_policy", capabilities = emptySet())

        registry.components = setOf(emptyCapComponent)

        val result = registry.getByCapabilities(
            setOf(PolicyCapability.MODIFIES_RADIO),
            matchAll = true
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `component with no capabilities passes matchAll for empty set`() {
        val emptyCapComponent = createTestComponent("empty_cap_policy", capabilities = emptySet())

        registry.components = setOf(emptyCapComponent)

        val result = registry.getByCapabilities(emptySet(), matchAll = true)

        assertEquals(1, result.size)
    }

    // ===== Index Consistency After Modifications =====

    @Test
    fun `indexes are consistent after multiple component replacements`() {
        // Initial set
        registry.components = setOf(
            createTestComponent("p1", capabilities = setOf(PolicyCapability.MODIFIES_RADIO))
        )
        assertEquals(1, registry.getByCapability(PolicyCapability.MODIFIES_RADIO).size)

        // Replace with different components
        registry.components = setOf(
            createTestComponent("p2", capabilities = setOf(PolicyCapability.MODIFIES_WIFI))
        )
        assertEquals(0, registry.getByCapability(PolicyCapability.MODIFIES_RADIO).size)
        assertEquals(1, registry.getByCapability(PolicyCapability.MODIFIES_WIFI).size)

        // Replace again
        registry.components = setOf(
            createTestComponent("p3", capabilities = setOf(PolicyCapability.MODIFIES_DISPLAY)),
            createTestComponent("p4", capabilities = setOf(PolicyCapability.MODIFIES_DISPLAY))
        )
        assertEquals(0, registry.getByCapability(PolicyCapability.MODIFIES_WIFI).size)
        assertEquals(2, registry.getByCapability(PolicyCapability.MODIFIES_DISPLAY).size)
    }

    // ===== Category Index Consistency =====

    @Test
    fun `category index is rebuilt correctly`() {
        registry.components = setOf(
            createTestComponent("t1", PolicyCategory.Toggle),
            createTestComponent("t2", PolicyCategory.Toggle),
            createTestComponent("c1", PolicyCategory.ConfigurableToggle)
        )

        assertEquals(2, registry.getByCategory(PolicyCategory.Toggle).size)
        assertEquals(1, registry.getByCategory(PolicyCategory.ConfigurableToggle).size)

        // Replace with different distribution
        registry.components = setOf(
            createTestComponent("t3", PolicyCategory.Toggle),
            createTestComponent("c2", PolicyCategory.ConfigurableToggle),
            createTestComponent("c3", PolicyCategory.ConfigurableToggle)
        )

        assertEquals(1, registry.getByCategory(PolicyCategory.Toggle).size)
        assertEquals(2, registry.getByCategory(PolicyCategory.ConfigurableToggle).size)
    }

    // ===== Query Combinations =====

    @Test
    fun `query with category and capabilities that don't overlap returns empty`() {
        registry.components = setOf(
            createTestComponent(
                "radio_toggle",
                PolicyCategory.Toggle,
                setOf(PolicyCapability.MODIFIES_RADIO)
            ),
            createTestComponent(
                "display_configurable",
                PolicyCategory.ConfigurableToggle,
                setOf(PolicyCapability.MODIFIES_DISPLAY)
            )
        )

        // Looking for Toggle + MODIFIES_DISPLAY (no match)
        val result = registry.query(
            category = PolicyCategory.Toggle,
            capabilities = setOf(PolicyCapability.MODIFIES_DISPLAY)
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `query with null category returns all components filtered by capabilities`() {
        registry.components = setOf(
            createTestComponent("p1", PolicyCategory.Toggle, setOf(PolicyCapability.MODIFIES_RADIO)),
            createTestComponent("p2", PolicyCategory.ConfigurableToggle, setOf(PolicyCapability.MODIFIES_RADIO)),
            createTestComponent("p3", PolicyCategory.Toggle, setOf(PolicyCapability.MODIFIES_DISPLAY))
        )

        val result = registry.query(
            category = null,
            capabilities = setOf(PolicyCapability.MODIFIES_RADIO)
        )

        assertEquals(2, result.size)
        assertTrue(result.all { it.hasCapability(PolicyCapability.MODIFIES_RADIO) })
    }

    // ===== Large Number of Components =====

    @Test
    fun `handles many components efficiently`() {
        // Create 100 components with various capabilities
        val components = (1..100).map { i ->
            val capabilities = when (i % 5) {
                0 -> setOf(PolicyCapability.MODIFIES_RADIO)
                1 -> setOf(PolicyCapability.MODIFIES_WIFI)
                2 -> setOf(PolicyCapability.MODIFIES_DISPLAY)
                3 -> setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.MODIFIES_WIFI)
                else -> emptySet()
            }
            createTestComponent("policy_$i", capabilities = capabilities)
        }.toSet()

        registry.components = components

        // 20 with just MODIFIES_RADIO + 20 with both = 40 total with MODIFIES_RADIO
        assertEquals(40, registry.getByCapability(PolicyCapability.MODIFIES_RADIO).size)

        // 20 with just MODIFIES_WIFI + 20 with both = 40 total with MODIFIES_WIFI
        assertEquals(40, registry.getByCapability(PolicyCapability.MODIFIES_WIFI).size)

        // 20 with MODIFIES_DISPLAY
        assertEquals(20, registry.getByCapability(PolicyCapability.MODIFIES_DISPLAY).size)

        // 20 with both capabilities
        assertEquals(
            20,
            registry.getByCapabilities(
                setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.MODIFIES_WIFI),
                matchAll = true
            ).size
        )
    }

    // ===== getPolicyState Edge Cases =====

    @Test
    fun `getPolicyState returns null for non-existent policy`() = runTest {
        registry.components = setOf(createTestComponent("existing"))

        val result = registry.getPolicyState("nonexistent")

        assertNull(result)
    }

    @Test
    fun `getPolicyState returns policy for existing policy`() = runTest {
        val component = createTestComponent("existing")
        registry.components = setOf(component)

        val result = registry.getPolicyState("existing")

        assertEquals("existing", result?.key?.policyName)
    }

    // ===== isRegistered Edge Cases =====

    @Test
    fun `isRegistered returns true for registered policy`() {
        val component = createTestComponent("registered")
        registry.components = setOf(component)

        assertTrue(registry.isRegistered(component.key))
    }

    @Test
    fun `isRegistered returns false after component is removed`() {
        val component = createTestComponent("to_remove")
        registry.components = setOf(component)

        assertTrue(registry.isRegistered(component.key))

        registry.components = emptySet()

        assertFalse(registry.isRegistered(component.key))
    }
}
