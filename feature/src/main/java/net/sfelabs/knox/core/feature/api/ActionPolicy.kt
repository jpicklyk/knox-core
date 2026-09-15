package net.sfelabs.knox.core.feature.api

import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.feature.ui.model.ConfigurationOption

/**
 * Base class for one-shot policies: operations that are performed rather than configured,
 * such as resetting settings or rebooting. Use with [PolicyCategory.Action].
 *
 * Unlike [BooleanStatePolicy] and [ConfigurableStatePolicy], an action has no state to read
 * from the device. The [PolicyContract] is mapped as follows:
 *
 * - [setState] performs the action by calling [execute]. The incoming [ActionPolicyState]
 *   carries no information and is ignored; the UI sends it purely as the trigger.
 * - [getState] never touches the device. It returns the outcome of the most recent run in
 *   this process (see [ActionPolicyState]), or [defaultValue] if the action has not run yet.
 *   Support therefore cannot be known up front: the state reports `isSupported = false`
 *   only after a run returns [ApiResult.NotSupported].
 * - The policy exposes no configuration options.
 *
 * Example:
 * ```kotlin
 * @PolicyDefinition(
 *     title = "Reset All Settings",
 *     description = "Performs the device's Reset All Settings operation.",
 *     category = PolicyCategory.Action
 * )
 * class ResetAllSettingsPolicy : ActionPolicy() {
 *     private val resetUseCase = ResetAllSettingsUseCase()
 *     override suspend fun execute(): ApiResult<Unit> = resetUseCase()
 * }
 * ```
 */
abstract class ActionPolicy : PolicyContract<ActionPolicyState>, PolicyUiConverter<ActionPolicyState> {

    override val defaultValue: ActionPolicyState = ActionPolicyState()

    @Volatile
    private var lastState: ActionPolicyState = defaultValue

    /** Performs the action. Called once per [setState]. */
    protected abstract suspend fun execute(): ApiResult<Unit>

    override suspend fun getState(parameters: PolicyParameters): ActionPolicyState = lastState

    override suspend fun setState(state: ActionPolicyState): ApiResult<Unit> {
        val result = execute()
        lastState = when (result) {
            is ApiResult.Success -> ActionPolicyState(lastRunSucceeded = true)
            is ApiResult.NotSupported -> ActionPolicyState(isSupported = false, lastRunSucceeded = false)
            is ApiResult.Error -> ActionPolicyState(
                error = result.apiError,
                exception = result.exception,
                lastRunSucceeded = false
            )
        }
        return result
    }

    /** The UI state carries nothing an action needs, so any trigger maps to a fresh request. */
    override fun fromUiState(uiEnabled: Boolean, options: List<ConfigurationOption>): ActionPolicyState =
        ActionPolicyState()

    override fun getConfigurationOptions(state: ActionPolicyState): List<ConfigurationOption> =
        emptyList()
}
