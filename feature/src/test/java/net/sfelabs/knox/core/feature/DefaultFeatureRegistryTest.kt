package net.sfelabs.knox.core.feature

import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase
import kotlinx.coroutines.test.runTest
import net.sfelabs.knox.core.domain.usecase.model.ApiError
import net.sfelabs.knox.core.domain.usecase.model.DefaultApiError
import net.sfelabs.knox.core.feature.api.PolicyCategory
import net.sfelabs.knox.core.feature.api.PolicyComponent
import net.sfelabs.knox.core.feature.api.PolicyKey
import net.sfelabs.knox.core.feature.api.PolicyState
import net.sfelabs.knox.core.feature.data.repository.DefaultPolicyRegistry
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler
import org.junit.Before
import org.junit.Test

class DefaultFeatureRegistryTest {
    // Test PolicyState implementation
    data class TestState(
        override val isEnabled: Boolean,
        override val isSupported: Boolean = true,
        override val error: ApiError? = null,
        override val exception: Throwable? = null
    ) : PolicyState {
        override fun withEnabled(enabled: Boolean): PolicyState {
            return copy(isEnabled = enabled)
        }

        override fun withError(error: ApiError?, exception: Throwable?): PolicyState {
            return copy(error = error, exception = exception)
        }
    }

    private lateinit var registry: DefaultPolicyRegistry
    private lateinit var mockComponent: PolicyComponent<PolicyState>
    private val mockKey = object : PolicyKey<PolicyState> {
        override val policyName = "test_feature"
    }
    private val mockHandler = mockk<PolicyHandler<PolicyState>>()

    @Before
    fun setup() {
        registry = DefaultPolicyRegistry()
        mockComponent = object : PolicyComponent<PolicyState> {
            override val policyName = "test_feature"
            override val title = "Test Policy"
            override val description = "some description"
            override val category = PolicyCategory.Toggle
            override val handler = mockHandler
            override val defaultValue = TestState(isEnabled = false)
            override val key = mockKey
        }
    }

    @Test
    fun `when getting features by category then only matching features are returned`() = runTest {
        coEvery { mockHandler.getState() } returns TestState(isEnabled = true)

        registry.components = setOf(mockComponent)

        val features = registry.getPolicies(PolicyCategory.Toggle)
        TestCase.assertEquals(1, features.size)
        TestCase.assertEquals(mockKey, features[0].key)
        TestCase.assertTrue(features[0].state.isEnabled)
    }

    @Test
    fun `when getting non-existent feature then returns null handler`() {
        registry.components = emptySet()
        TestCase.assertNull(registry.getHandler(mockKey))
    }

    @Suppress("USELESS_CAST")
    @Test
    fun `when feature state has error then returns feature with error state`() = runTest {
        val error = DefaultApiError.UnexpectedError()
        coEvery { mockHandler.getState() } returns TestState(
            isEnabled = false,
            error = error
        )

        registry.components = setOf(mockComponent)

        val features = registry.getPolicies(PolicyCategory.Toggle)
        TestCase.assertEquals(1, features.size)
        val feature = features[0]
        TestCase.assertEquals(mockKey, feature.key)
        TestCase.assertEquals(false, feature.state.isEnabled)
        TestCase.assertEquals(error, feature.state.error)
    }

    @Test
    fun `when getting features by different category then returns empty list`() = runTest {
        coEvery { mockHandler.getState() } returns TestState(isEnabled = true)
        registry.components = setOf(mockComponent)
        val features = registry.getPolicies(PolicyCategory.ConfigurableToggle) // Different category
        TestCase.assertTrue(features.isEmpty())
    }

    @Test
    fun `when multiple components exist then all are retrievable`() = runTest {
        // Setup second component
        val mockKey2 = object : PolicyKey<PolicyState> {
            override val policyName = "test_feature_2"
        }
        val mockHandler2 = mockk<PolicyHandler<PolicyState>>()
        val mockComponent2 = object : PolicyComponent<PolicyState> {
            override val policyName = "test_feature_2"
            override val title = "Test Policy 2"
            override val description = "some description 2"
            override val category = PolicyCategory.Toggle
            override val handler = mockHandler2
            override val defaultValue = TestState(isEnabled = false)
            override val key = mockKey2
        }

        coEvery { mockHandler.getState() } returns TestState(isEnabled = true)
        coEvery { mockHandler2.getState() } returns TestState(isEnabled = false)

        registry.components = setOf(mockComponent, mockComponent2)

        val features = registry.getPolicies(PolicyCategory.Toggle)
        TestCase.assertEquals(2, features.size)
        TestCase.assertTrue(features.any { it.key.policyName == "test_feature" && it.state.isEnabled })
        TestCase.assertTrue(features.any { it.key.policyName == "test_feature_2" && !it.state.isEnabled })
    }

    @Test
    fun `when getting handler with different type then returns null`() {
        data class OtherState(
            override val isEnabled: Boolean,
            override val isSupported: Boolean = true,
            override val error: ApiError? = null,
            override val exception: Throwable? = null
        ) : PolicyState {
            override fun withEnabled(enabled: Boolean): PolicyState {
                return copy(isEnabled = enabled)
            }

            override fun withError(error: ApiError?, exception: Throwable?): PolicyState {
                return copy(error = error, exception = exception)
            }
        }

        val otherKey = object : PolicyKey<OtherState> { override val policyName = "test_feature" }
        registry.components = setOf(mockComponent)
        TestCase.assertNull(registry.getHandler(otherKey))
    }

    @Test
    fun `when unsupported feature then returns state with isSupported false`() = runTest {
        coEvery { mockHandler.getState() } returns TestState(
            isEnabled = false,
            isSupported = false
        )

        registry.components = setOf(mockComponent)

        val features = registry.getPolicies(PolicyCategory.Toggle)
        TestCase.assertEquals(1, features.size)
        TestCase.assertEquals(false, features[0].state.isSupported)
    }

    @Test
    fun `when getting component then returns correct component`() {
        registry.components = setOf(mockComponent)
        val component = registry.getComponent(mockKey)
        TestCase.assertEquals(mockComponent, component)
    }

    @Test
    fun `when getting non-existent component then returns null`() {
        registry.components = emptySet()
        val nonExistentKey = object : PolicyKey<TestState> {
            override val policyName = "non_existent"
        }
        TestCase.assertNull(registry.getComponent(nonExistentKey))
    }
}
