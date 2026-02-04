package net.sfelabs.knox.core.feature.api

import net.sfelabs.knox.core.feature.ui.model.ConfigurationOption

interface PolicyConfiguration<T : PolicyState, D: Any> {
    val stateMapping: StateMapping

    /**
     * Transform raw API data into a PolicyState, applying state mapping
     */
    fun fromApiData(apiData: D): T

    /**
     * Transform PolicyState into API data, applying state mapping
     */
    fun toApiData(state: T): D

    /**
     * Convert UI state to PolicyState.
     *
     * IMPORTANT: This method only receives UI-derived state (enabled flag and configuration options).
     * It cannot preserve non-UI state fields that exist in the PolicyState (e.g., device capabilities,
     * support flags, cached API data). The returned state will have default values for any fields
     * not derivable from the UI inputs.
     *
     * Consumers should NOT use the returned state directly for UI updates after a successful
     * setPolicyState() call. Instead, call getState() to refresh the complete state including
     * all device-derived fields.
     *
     * Example of fields that may be lost:
     * - HdmState.supportedMask (device capability bitmask)
     * - Any cached/computed fields not represented in ConfigurationOptions
     */
    fun fromUiState(uiEnabled: Boolean, options: List<ConfigurationOption>): T

//    /**
//     * Convert PolicyState to UI configuration options
//     */
//    fun toUiState(state: T, component: PolicyComponent<T>): PolicyUiState {
//        return PolicyUiState.ConfigurableToggle(
//            title = component.title,
//            featureName = component.featureName,
//            description = component.description,
//            isEnabled = state.isEnabled,
//            isSupported = state.isSupported,
//            error = state.error?.message,
//            configurationOptions = getConfigurationOptions(state)
//        )
//    }

    /**
     * Maps between API and domain state based on stateMapping
     */
    fun mapEnabled(enabled: Boolean): Boolean = when (stateMapping) {
        StateMapping.DIRECT -> enabled
        StateMapping.INVERTED -> !enabled
    }

    fun getConfigurationOptions(state: T): List<ConfigurationOption>
}