package net.sfelabs.knox.core.feature.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.sfelabs.knox.core.domain.usecase.model.ApiError
import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.domain.usecase.model.DefaultApiError
import net.sfelabs.knox.core.feature.api.PolicyCapability
import net.sfelabs.knox.core.feature.api.PolicyCategory
import net.sfelabs.knox.core.feature.api.PolicyComponent
import net.sfelabs.knox.core.feature.api.PolicyKey
import net.sfelabs.knox.core.feature.api.PolicyState
import net.sfelabs.knox.core.feature.api.PolicyUiConverter
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler
import net.sfelabs.knox.core.feature.ui.model.ConfigurationOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PolicyRegistrySetAndRefreshTest {

    // Test state with an extra field to verify refresh returns device state
    private data class TestState(
        override val isEnabled: Boolean,
        override val isSupported: Boolean = true,
        override val error: ApiError? = null,
        override val exception: Throwable? = null,
        val deviceDerivedField: String = ""
    ) : PolicyState {
        override fun withEnabled(enabled: Boolean) = copy(isEnabled = enabled)
        override fun withError(error: ApiError?, exception: Throwable?) = copy(error = error, exception = exception)
    }

    private val testKey = object : PolicyKey<TestState> {
        override val policyName = "test_policy"
    }

    private val testUiConverter = object : PolicyUiConverter<TestState> {
        override fun fromUiState(uiEnabled: Boolean, options: List<ConfigurationOption>): TestState {
            return TestState(isEnabled = uiEnabled)
        }
        override fun getConfigurationOptions(state: TestState): List<ConfigurationOption> = emptyList()
    }

    private lateinit var mockHandler: PolicyHandler<TestState>
    private lateinit var registry: DefaultPolicyRegistry

    @Before
    fun setUp() {
        mockHandler = mockk()

        val testComponent = object : PolicyComponent<TestState> {
            override val policyName = "test_policy"
            override val title = "Test Policy"
            override val description = "Test description"
            override val category = PolicyCategory.Toggle
            override val handler = mockHandler
            override val defaultValue = TestState(isEnabled = false)
            override val key = testKey
            override val uiConverter = testUiConverter
            override val capabilities = emptySet<PolicyCapability>()
        }

        registry = DefaultPolicyRegistry()
        registry.components = setOf(testComponent)
    }

    @Test
    fun `setAndRefreshPolicyState returns refreshed state on success`() = runTest {
        // Given
        val inputState = TestState(isEnabled = true, deviceDerivedField = "")
        val deviceState = TestState(isEnabled = true, deviceDerivedField = "fromDevice")

        coEvery { mockHandler.setState(inputState) } returns ApiResult.Success(Unit)
        coEvery { mockHandler.getState(any()) } returns deviceState

        // When
        val result = registry.setAndRefreshPolicyState(testKey, inputState)

        // Then
        assertTrue("Result should be success", result is ApiResult.Success)
        val policy = (result as ApiResult.Success).data
        assertEquals("fromDevice", (policy.state.value as TestState).deviceDerivedField)
    }

    @Test
    fun `setAndRefreshPolicyState returns error when setState fails`() = runTest {
        // Given
        val inputState = TestState(isEnabled = true)
        val error = DefaultApiError.UnexpectedError("Set failed")

        coEvery { mockHandler.setState(inputState) } returns ApiResult.Error(error)

        // When
        val result = registry.setAndRefreshPolicyState(testKey, inputState)

        // Then
        assertTrue("Result should be error", result is ApiResult.Error)
        assertEquals("Set failed", (result as ApiResult.Error).apiError.message)

        // Should not call getState when setState fails
        coVerify(exactly = 0) { mockHandler.getState(any()) }
    }

    @Test
    fun `setAndRefreshPolicyState returns NotSupported when setState returns NotSupported`() = runTest {
        // Given
        val inputState = TestState(isEnabled = true)

        coEvery { mockHandler.setState(inputState) } returns ApiResult.NotSupported

        // When
        val result = registry.setAndRefreshPolicyState(testKey, inputState)

        // Then
        assertTrue("Result should be NotSupported", result is ApiResult.NotSupported)
        coVerify(exactly = 0) { mockHandler.getState(any()) }
    }

    @Test
    fun `setAndRefreshPolicyState returns error when getState fails after successful set`() = runTest {
        // Given
        val inputState = TestState(isEnabled = true)

        coEvery { mockHandler.setState(inputState) } returns ApiResult.Success(Unit)
        coEvery { mockHandler.getState(any()) } throws RuntimeException("Refresh failed")

        // When
        val result = registry.setAndRefreshPolicyState(testKey, inputState)

        // Then
        assertTrue("Result should be error", result is ApiResult.Error)
        assertTrue(
            "Error message should mention refresh failed",
            (result as ApiResult.Error).apiError.message.contains("refresh failed")
        )
    }

    @Test
    fun `setAndRefreshPolicyState returns error for unknown policy`() = runTest {
        // Given
        val unknownKey = object : PolicyKey<TestState> {
            override val policyName = "unknown_policy"
        }
        val inputState = TestState(isEnabled = true)

        // When
        val result = registry.setAndRefreshPolicyState(unknownKey, inputState)

        // Then
        assertTrue("Result should be error", result is ApiResult.Error)
        assertTrue(
            "Error should mention handler not found",
            (result as ApiResult.Error).apiError.message.contains("not found")
        )
    }
}
