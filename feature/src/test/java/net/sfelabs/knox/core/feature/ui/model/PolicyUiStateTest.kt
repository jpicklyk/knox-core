package net.sfelabs.knox.core.feature.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolicyUiStateTest {

    @Test
    fun `Toggle copyWithLoading clears existing error`() {
        // Given
        val stateWithError = PolicyUiState.Toggle(
            isSupported = true,
            title = "Test Policy",
            policyName = "test_policy",
            description = "Test description",
            isEnabled = true,
            isLoading = false,
            error = "Previous error message"
        )

        // When
        val loadingState = stateWithError.copyWithLoading(isLoading = true)

        // Then
        assertNull("Error should be cleared when loading starts", loadingState.error)
        assertEquals(true, loadingState.isLoading)
    }

    @Test
    fun `Toggle copyWithLoading preserves other fields`() {
        // Given
        val original = PolicyUiState.Toggle(
            isSupported = true,
            title = "Test Policy",
            policyName = "test_policy",
            description = "Test description",
            isEnabled = true,
            isLoading = false,
            error = "Some error"
        )

        // When
        val result = original.copyWithLoading(isLoading = true)

        // Then
        assertEquals(original.title, result.title)
        assertEquals(original.policyName, result.policyName)
        assertEquals(original.description, result.description)
        assertEquals(original.isEnabled, result.isEnabled)
        assertEquals(original.isSupported, result.isSupported)
    }

    @Test
    fun `Toggle copyWithError sets error and clears loading`() {
        // Given
        val loadingState = PolicyUiState.Toggle(
            isSupported = true,
            title = "Test Policy",
            policyName = "test_policy",
            description = "Test description",
            isEnabled = true,
            isLoading = true,
            error = null
        )

        // When
        val errorState = loadingState.copyWithError(error = "New error")

        // Then
        assertEquals("New error", errorState.error)
        assertEquals(false, errorState.isLoading)
    }

    @Test
    fun `ConfigurableToggle copyWithLoading clears existing error`() {
        // Given
        val stateWithError = PolicyUiState.ConfigurableToggle(
            isSupported = true,
            title = "Test Policy",
            policyName = "test_policy",
            description = "Test description",
            isEnabled = true,
            isLoading = false,
            error = "Previous error message",
            configurationOptions = listOf(
                ConfigurationOption.Toggle(
                    key = "option1",
                    label = "Option 1",
                    isEnabled = true
                )
            )
        )

        // When
        val loadingState = stateWithError.copyWithLoading(isLoading = true)

        // Then
        assertNull("Error should be cleared when loading starts", loadingState.error)
        assertEquals(true, loadingState.isLoading)
    }

    @Test
    fun `ConfigurableToggle copyWithLoading preserves configuration options`() {
        // Given
        val options = listOf(
            ConfigurationOption.Toggle(key = "opt1", label = "Option 1", isEnabled = true),
            ConfigurationOption.Toggle(key = "opt2", label = "Option 2", isEnabled = false)
        )
        val original = PolicyUiState.ConfigurableToggle(
            isSupported = true,
            title = "Test Policy",
            policyName = "test_policy",
            description = "Test description",
            isEnabled = true,
            isLoading = false,
            error = "Some error",
            configurationOptions = options
        )

        // When
        val result = original.copyWithLoading(isLoading = true)

        // Then
        assertEquals(options, result.configurationOptions)
    }

    @Test
    fun `ConfigurableToggle copyWithError sets error and clears loading`() {
        // Given
        val loadingState = PolicyUiState.ConfigurableToggle(
            isSupported = true,
            title = "Test Policy",
            policyName = "test_policy",
            description = "Test description",
            isEnabled = true,
            isLoading = true,
            error = null,
            configurationOptions = emptyList()
        )

        // When
        val errorState = loadingState.copyWithError(error = "New error")

        // Then
        assertEquals("New error", errorState.error)
        assertEquals(false, errorState.isLoading)
    }
}
