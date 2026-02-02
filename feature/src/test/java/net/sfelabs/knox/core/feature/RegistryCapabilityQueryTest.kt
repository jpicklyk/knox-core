package net.sfelabs.knox.core.feature

import io.mockk.coEvery
import io.mockk.mockk
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for capability-based query functionality in DefaultPolicyRegistry.
 */
class RegistryCapabilityQueryTest {

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

    private lateinit var registry: DefaultPolicyRegistry

    // Test components
    private lateinit var radioPolicy1: PolicyComponent<TestState>
    private lateinit var radioPolicy2: PolicyComponent<TestState>
    private lateinit var displayPolicy: PolicyComponent<TestState>
    private lateinit var multiCapabilityPolicy: PolicyComponent<TestState>
    private lateinit var noCapabilityPolicy: PolicyComponent<TestState>

    private fun createTestComponent(
        name: String,
        category: PolicyCategory,
        capabilities: Set<PolicyCapability>
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

        radioPolicy1 = createTestComponent(
            "band_locking_5g",
            PolicyCategory.ConfigurableToggle,
            setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.REQUIRES_SIM)
        )

        radioPolicy2 = createTestComponent(
            "disable_2g",
            PolicyCategory.Toggle,
            setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.AFFECTS_CONNECTIVITY)
        )

        displayPolicy = createTestComponent(
            "night_vision",
            PolicyCategory.Toggle,
            setOf(PolicyCapability.MODIFIES_DISPLAY, PolicyCapability.EASILY_REVERSIBLE)
        )

        multiCapabilityPolicy = createTestComponent(
            "wifi_radio_policy",
            PolicyCategory.ConfigurableToggle,
            setOf(
                PolicyCapability.MODIFIES_RADIO,
                PolicyCapability.MODIFIES_WIFI,
                PolicyCapability.AFFECTS_CONNECTIVITY
            )
        )

        noCapabilityPolicy = createTestComponent(
            "basic_policy",
            PolicyCategory.Toggle,
            emptySet()
        )

        registry.components = setOf(
            radioPolicy1, radioPolicy2, displayPolicy, multiCapabilityPolicy, noCapabilityPolicy
        )
    }

    // getByCapability tests

    @Test
    fun `getByCapability returns all policies with matching capability`() {
        val radioPolicies = registry.getByCapability(PolicyCapability.MODIFIES_RADIO)

        assertEquals(3, radioPolicies.size)
        assertTrue(radioPolicies.any { it.policyName == "band_locking_5g" })
        assertTrue(radioPolicies.any { it.policyName == "disable_2g" })
        assertTrue(radioPolicies.any { it.policyName == "wifi_radio_policy" })
    }

    @Test
    fun `getByCapability returns empty list when no policies have capability`() {
        val audioP = registry.getByCapability(PolicyCapability.MODIFIES_AUDIO)

        assertTrue(audioP.isEmpty())
    }

    @Test
    fun `getByCapability returns single policy when only one matches`() {
        val displayPolicies = registry.getByCapability(PolicyCapability.MODIFIES_DISPLAY)

        assertEquals(1, displayPolicies.size)
        assertEquals("night_vision", displayPolicies[0].policyName)
    }

    // getByCapabilities tests (matchAll = false)

    @Test
    fun `getByCapabilities with matchAll false returns policies with any capability`() {
        val policies = registry.getByCapabilities(
            setOf(PolicyCapability.MODIFIES_DISPLAY, PolicyCapability.MODIFIES_WIFI),
            matchAll = false
        )

        assertEquals(2, policies.size)
        assertTrue(policies.any { it.policyName == "night_vision" })
        assertTrue(policies.any { it.policyName == "wifi_radio_policy" })
    }

    @Test
    fun `getByCapabilities with empty set returns all policies`() {
        val policies = registry.getByCapabilities(emptySet(), matchAll = false)

        assertEquals(5, policies.size)
    }

    @Test
    fun `getByCapabilities with single capability same as getByCapability`() {
        val singleCapPolicies = registry.getByCapabilities(
            setOf(PolicyCapability.MODIFIES_RADIO),
            matchAll = false
        )
        val directPolicies = registry.getByCapability(PolicyCapability.MODIFIES_RADIO)

        assertEquals(directPolicies.size, singleCapPolicies.size)
        assertEquals(directPolicies.map { it.policyName }.toSet(), singleCapPolicies.map { it.policyName }.toSet())
    }

    // getByCapabilities tests (matchAll = true)

    @Test
    fun `getByCapabilities with matchAll true returns only policies with all capabilities`() {
        val policies = registry.getByCapabilities(
            setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.AFFECTS_CONNECTIVITY),
            matchAll = true
        )

        assertEquals(2, policies.size)
        assertTrue(policies.any { it.policyName == "disable_2g" })
        assertTrue(policies.any { it.policyName == "wifi_radio_policy" })
    }

    @Test
    fun `getByCapabilities with matchAll true returns empty when no policy has all capabilities`() {
        val policies = registry.getByCapabilities(
            setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.MODIFIES_DISPLAY),
            matchAll = true
        )

        assertTrue(policies.isEmpty())
    }

    @Test
    fun `getByCapabilities with matchAll true and single capability works correctly`() {
        val policies = registry.getByCapabilities(
            setOf(PolicyCapability.MODIFIES_DISPLAY),
            matchAll = true
        )

        assertEquals(1, policies.size)
        assertEquals("night_vision", policies[0].policyName)
    }

    // getByCategory tests

    @Test
    fun `getByCategory returns all policies with matching category`() {
        val togglePolicies = registry.getByCategory(PolicyCategory.Toggle)

        assertEquals(3, togglePolicies.size)
        assertTrue(togglePolicies.any { it.policyName == "disable_2g" })
        assertTrue(togglePolicies.any { it.policyName == "night_vision" })
        assertTrue(togglePolicies.any { it.policyName == "basic_policy" })
    }

    @Test
    fun `getByCategory returns empty list for category with no policies`() {
        // Create fresh registry with only toggle policies
        val toggleOnlyRegistry = DefaultPolicyRegistry()
        toggleOnlyRegistry.components = setOf(displayPolicy, noCapabilityPolicy)

        val configurablePolicies = toggleOnlyRegistry.getByCategory(PolicyCategory.ConfigurableToggle)

        assertTrue(configurablePolicies.isEmpty())
    }

    // query tests (combined filtering)

    @Test
    fun `query with only category returns policies in that category`() {
        val policies = registry.query(category = PolicyCategory.Toggle)

        assertEquals(3, policies.size)
        assertTrue(policies.all { it.category == PolicyCategory.Toggle })
    }

    @Test
    fun `query with only capabilities returns policies with any capability`() {
        val policies = registry.query(
            capabilities = setOf(PolicyCapability.MODIFIES_RADIO)
        )

        assertEquals(3, policies.size)
    }

    @Test
    fun `query with category and capabilities filters by both`() {
        val policies = registry.query(
            category = PolicyCategory.Toggle,
            capabilities = setOf(PolicyCapability.MODIFIES_RADIO)
        )

        assertEquals(1, policies.size)
        assertEquals("disable_2g", policies[0].policyName)
    }

    @Test
    fun `query with matchAllCapabilities true requires all capabilities`() {
        val policies = registry.query(
            capabilities = setOf(
                PolicyCapability.MODIFIES_RADIO,
                PolicyCapability.MODIFIES_WIFI
            ),
            matchAllCapabilities = true
        )

        assertEquals(1, policies.size)
        assertEquals("wifi_radio_policy", policies[0].policyName)
    }

    @Test
    fun `query with category and matchAllCapabilities combines filters`() {
        val policies = registry.query(
            category = PolicyCategory.ConfigurableToggle,
            capabilities = setOf(
                PolicyCapability.MODIFIES_RADIO,
                PolicyCapability.AFFECTS_CONNECTIVITY
            ),
            matchAllCapabilities = true
        )

        assertEquals(1, policies.size)
        assertEquals("wifi_radio_policy", policies[0].policyName)
    }

    @Test
    fun `query with no filters returns all policies`() {
        val policies = registry.query()

        assertEquals(5, policies.size)
    }

    @Test
    fun `query returns empty list when no policies match all criteria`() {
        val policies = registry.query(
            category = PolicyCategory.Toggle,
            capabilities = setOf(PolicyCapability.MODIFIES_WIFI)
        )

        assertTrue(policies.isEmpty())
    }

    // getAllComponents tests

    @Test
    fun `getAllComponents returns all registered components`() {
        val components = registry.getAllComponents()

        assertEquals(5, components.size)
    }

    @Test
    fun `getAllComponents returns empty list when no components registered`() {
        val emptyRegistry = DefaultPolicyRegistry()
        val components = emptyRegistry.getAllComponents()

        assertTrue(components.isEmpty())
    }

    // Index rebuilding tests

    @Test
    fun `indexes are rebuilt when components are set`() {
        // Initial state
        assertEquals(3, registry.getByCapability(PolicyCapability.MODIFIES_RADIO).size)

        // Add new component with radio capability
        val newRadioPolicy = createTestComponent(
            "new_radio_policy",
            PolicyCategory.Toggle,
            setOf(PolicyCapability.MODIFIES_RADIO)
        )
        registry.components = registry.components + newRadioPolicy

        // Index should be updated
        assertEquals(4, registry.getByCapability(PolicyCapability.MODIFIES_RADIO).size)
    }

    @Test
    fun `indexes handle component removal correctly`() {
        // Initial state
        assertEquals(3, registry.getByCapability(PolicyCapability.MODIFIES_RADIO).size)

        // Remove a component
        registry.components = registry.components - radioPolicy1

        // Index should be updated
        assertEquals(2, registry.getByCapability(PolicyCapability.MODIFIES_RADIO).size)
    }

    @Test
    fun `indexes handle complete component replacement`() {
        // Replace all components
        val singlePolicy = createTestComponent(
            "only_policy",
            PolicyCategory.Toggle,
            setOf(PolicyCapability.MODIFIES_AUDIO)
        )
        registry.components = setOf(singlePolicy)

        assertEquals(1, registry.getAllComponents().size)
        assertEquals(0, registry.getByCapability(PolicyCapability.MODIFIES_RADIO).size)
        assertEquals(1, registry.getByCapability(PolicyCapability.MODIFIES_AUDIO).size)
    }
}
