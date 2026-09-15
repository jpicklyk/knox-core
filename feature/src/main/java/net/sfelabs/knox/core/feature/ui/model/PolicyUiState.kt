package net.sfelabs.knox.core.feature.ui.model

sealed class PolicyUiState {
    abstract val isSupported: Boolean
    abstract val title: String
    abstract val policyName: String
    abstract val description: String
    abstract val isLoading: Boolean
    abstract val error: String?
    abstract val isEnabled: Boolean

    abstract fun copyWithError(error: String?): PolicyUiState
    abstract fun copyWithLoading(isLoading: Boolean): PolicyUiState

    data class Toggle(
        override val isSupported: Boolean,
        override val title: String,
        override val policyName: String,
        override val description: String,
        override val isEnabled: Boolean,
        override val isLoading: Boolean = false,
        override val error: String? = null,
    ) : PolicyUiState() {
        override fun copyWithError(error: String?) = copy(isLoading = false, error = error)
        override fun copyWithLoading(isLoading: Boolean) = copy(isLoading = isLoading, error = null)
    }

    data class ConfigurableToggle(
        override val isSupported: Boolean,
        override val title: String,
        override val policyName: String,
        override val description: String,
        override val isEnabled: Boolean,
        override val isLoading: Boolean = false,
        override val error: String? = null,
        val configurationOptions: List<ConfigurationOption>
    ) : PolicyUiState() {
        override fun copyWithError(error: String?) = copy(isLoading = false, error = error)
        override fun copyWithLoading(isLoading: Boolean) = copy(isLoading = isLoading, error = null)
    }

    /**
     * A one-shot action rendered as a button rather than a switch. [isEnabled] is always
     * `false` because an action has no on/off state; [lastRunSucceeded] is `null` until the
     * action has been run in the current process.
     */
    data class Action(
        override val isSupported: Boolean,
        override val title: String,
        override val policyName: String,
        override val description: String,
        override val isLoading: Boolean = false,
        override val error: String? = null,
        val lastRunSucceeded: Boolean? = null
    ) : PolicyUiState() {
        override val isEnabled: Boolean
            get() = false

        override fun copyWithError(error: String?) = copy(isLoading = false, error = error)
        override fun copyWithLoading(isLoading: Boolean) = copy(isLoading = isLoading, error = null)
    }

    fun currentOptions(): List<ConfigurationOption> = when (this) {
        is Toggle -> emptyList()
        is Action -> emptyList()
        is ConfigurableToggle -> configurationOptions
    }
}