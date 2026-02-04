package net.sfelabs.knox.core.feature.domain.registry

import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.feature.api.PolicyCapability
import net.sfelabs.knox.core.feature.api.PolicyCategory
import net.sfelabs.knox.core.feature.api.PolicyComponent
import net.sfelabs.knox.core.feature.api.PolicyKey
import net.sfelabs.knox.core.feature.api.PolicyState
import net.sfelabs.knox.core.feature.domain.model.Policy
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler

/**
 * Registry for policy components providing lookup, querying, and state management.
 *
 * The registry supports multiple query dimensions:
 * - By key (exact match)
 * - By category (UI grouping)
 * - By capability (intrinsic properties)
 * - Combined queries
 *
 * ## Setting Policy State
 *
 * Two methods are available for setting state:
 *
 * - [setAndRefreshPolicyState]: Sets state and returns the refreshed state from device.
 *   Use when you need the complete state after setting (recommended for UI updates).
 *
 * - [setPolicyState]: Sets state and returns success/failure only.
 *   Use when you don't need the refreshed state or will handle refresh separately.
 */
interface PolicyRegistry {
    // Core lookup methods
    fun <T : PolicyState> getHandler(key: PolicyKey<T>): PolicyHandler<T>?
    suspend fun getAllPolicies(): List<Policy<PolicyState>>
    suspend fun getPolicies(category: PolicyCategory): List<Policy<PolicyState>>
    fun isRegistered(key: PolicyKey<*>): Boolean
    fun getComponent(key: PolicyKey<*>): PolicyComponent<out PolicyState>?
    suspend fun getPolicyState(featureName: String): Policy<PolicyState>?

    /**
     * Sets a policy state without returning the updated state.
     *
     * Note: The [state] parameter from [PolicyConfiguration.fromUiState][net.sfelabs.knox.core.feature.api.PolicyConfiguration.fromUiState]
     * may be missing device-derived fields. If you need the complete state after setting,
     * use [setAndRefreshPolicyState] instead, or manually call [getPolicyState] after success.
     *
     * @param policyKey The type-safe key for the policy
     * @param state The new state to set
     * @return Success or error result
     * @see setAndRefreshPolicyState
     */
    suspend fun <T : PolicyState> setPolicyState(policyKey: PolicyKey<T>, state: T): ApiResult<Unit>

    /**
     * Sets a policy state and returns the refreshed state from the device.
     *
     * This combines [setPolicyState] and [getPolicyState] into a single operation.
     * Use this when you need the complete state after setting, including device-derived
     * fields that are not preserved by [PolicyConfiguration.fromUiState][net.sfelabs.knox.core.feature.api.PolicyConfiguration.fromUiState]
     * (e.g., `HdmState.supportedMask`).
     *
     * @param policyKey The type-safe key for the policy
     * @param state The new state to set
     * @return The refreshed policy on success, or an error result
     * @see setPolicyState
     */
    suspend fun <T : PolicyState> setAndRefreshPolicyState(
        policyKey: PolicyKey<T>,
        state: T
    ): ApiResult<Policy<T>>

    // Capability-based queries

    /**
     * Get all components that have the specified capability.
     */
    fun getByCapability(capability: PolicyCapability): List<PolicyComponent<out PolicyState>>

    /**
     * Get all components that have any/all of the specified capabilities.
     *
     * @param capabilities The capabilities to match
     * @param matchAll If true, returns only components with ALL capabilities. If false, returns components with ANY.
     */
    fun getByCapabilities(
        capabilities: Set<PolicyCapability>,
        matchAll: Boolean = false
    ): List<PolicyComponent<out PolicyState>>

    /**
     * Get all components in the specified category.
     */
    fun getByCategory(category: PolicyCategory): List<PolicyComponent<out PolicyState>>

    /**
     * Query components with optional filters.
     *
     * @param category Optional category filter
     * @param capabilities Optional capability filter
     * @param matchAllCapabilities If true, requires ALL capabilities. If false, requires ANY.
     */
    fun query(
        category: PolicyCategory? = null,
        capabilities: Set<PolicyCapability>? = null,
        matchAllCapabilities: Boolean = false
    ): List<PolicyComponent<out PolicyState>>

    /**
     * Get all registered components.
     */
    fun getAllComponents(): List<PolicyComponent<out PolicyState>>
}