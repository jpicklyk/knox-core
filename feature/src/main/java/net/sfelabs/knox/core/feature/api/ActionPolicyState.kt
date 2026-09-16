package net.sfelabs.knox.core.feature.api

import net.sfelabs.knox.core.domain.usecase.model.ApiError

/**
 * State for one-shot [ActionPolicy] implementations.
 *
 * An action has nothing to read back from the device, so this state only records device
 * support and the outcome of the most recent run:
 *
 * - [isEnabled] is always `false`. Actions have no persistent on/off state; the value exists
 *   only to satisfy [PolicyState] so actions flow through the same registry and UI plumbing
 *   as toggles.
 * - [lastRunSucceeded] is `null` until the action has been executed at least once in the
 *   current process, then `true` or `false` for the latest run.
 * - [isSupported] flips to `false` once a run reports
 *   [net.sfelabs.knox.core.domain.usecase.model.ApiResult.NotSupported].
 */
data class ActionPolicyState(
    override val isSupported: Boolean = true,
    override val error: ApiError? = null,
    override val exception: Throwable? = null,
    val lastRunSucceeded: Boolean? = null
) : PolicyState {
    override val isEnabled: Boolean
        get() = false

    /** Actions have no enabled state, so the request is ignored. */
    override fun withEnabled(enabled: Boolean): PolicyState = this

    override fun withError(error: ApiError?, exception: Throwable?): PolicyState =
        copy(error = error, exception = exception)
}
