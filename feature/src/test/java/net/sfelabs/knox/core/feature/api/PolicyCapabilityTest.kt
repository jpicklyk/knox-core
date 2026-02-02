package net.sfelabs.knox.core.feature.api

import io.mockk.mockk
import net.sfelabs.knox.core.domain.usecase.model.ApiError
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler
import net.sfelabs.knox.core.feature.ui.model.ConfigurationOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyCapabilityTest {

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

    @Test
    fun `hasCapability returns true when component has capability`() {
        val component = createTestComponent(
            "test_policy",
            setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.REQUIRES_SIM)
        )

        assertTrue(component.hasCapability(PolicyCapability.MODIFIES_RADIO))
        assertTrue(component.hasCapability(PolicyCapability.REQUIRES_SIM))
    }

    @Test
    fun `hasCapability returns false when component does not have capability`() {
        val component = createTestComponent(
            "test_policy",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )

        assertFalse(component.hasCapability(PolicyCapability.MODIFIES_WIFI))
        assertFalse(component.hasCapability(PolicyCapability.REQUIRES_SIM))
    }

    @Test
    fun `hasAllCapabilities returns true when component has all requested capabilities`() {
        val component = createTestComponent(
            "test_policy",
            setOf(
                PolicyCapability.MODIFIES_RADIO,
                PolicyCapability.REQUIRES_SIM,
                PolicyCapability.AFFECTS_CONNECTIVITY
            )
        )

        assertTrue(
            component.hasAllCapabilities(
                setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.REQUIRES_SIM)
            )
        )
    }

    @Test
    fun `hasAllCapabilities returns false when component is missing a capability`() {
        val component = createTestComponent(
            "test_policy",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )

        assertFalse(
            component.hasAllCapabilities(
                setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.REQUIRES_SIM)
            )
        )
    }

    @Test
    fun `hasAllCapabilities returns true for empty set`() {
        val component = createTestComponent(
            "test_policy",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )

        assertTrue(component.hasAllCapabilities(emptySet()))
    }

    @Test
    fun `hasAnyCapability returns true when component has at least one capability`() {
        val component = createTestComponent(
            "test_policy",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )

        assertTrue(
            component.hasAnyCapability(
                setOf(PolicyCapability.MODIFIES_RADIO, PolicyCapability.MODIFIES_WIFI)
            )
        )
    }

    @Test
    fun `hasAnyCapability returns false when component has no matching capabilities`() {
        val component = createTestComponent(
            "test_policy",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )

        assertFalse(
            component.hasAnyCapability(
                setOf(PolicyCapability.MODIFIES_WIFI, PolicyCapability.MODIFIES_BLUETOOTH)
            )
        )
    }

    @Test
    fun `hasAnyCapability returns false for empty set`() {
        val component = createTestComponent(
            "test_policy",
            setOf(PolicyCapability.MODIFIES_RADIO)
        )

        assertFalse(component.hasAnyCapability(emptySet()))
    }

    @Test
    fun `PolicyCapability enum contains all expected device-centric capabilities`() {
        val expectedCapabilities = setOf(
            "MODIFIES_RADIO",
            "MODIFIES_WIFI",
            "MODIFIES_BLUETOOTH",
            "MODIFIES_DISPLAY",
            "MODIFIES_AUDIO",
            "MODIFIES_CHARGING",
            "MODIFIES_CALLING",
            "MODIFIES_HARDWARE",
            "MODIFIES_SECURITY",
            "MODIFIES_NETWORK",
            "REQUIRES_SIM",
            "REQUIRES_HDM",
            "REQUIRES_DUAL_SIM",
            "SECURITY_SENSITIVE",
            "AFFECTS_CONNECTIVITY",
            "AFFECTS_BATTERY",
            "REQUIRES_REBOOT",
            "EASILY_REVERSIBLE",
            "PERSISTENT_ACROSS_REBOOT"
        )

        val actualCapabilities = PolicyCapability.entries.map { it.name }.toSet()
        assertEquals(expectedCapabilities, actualCapabilities)
    }
}
