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
 */
interface PolicyRegistry {
    // Existing methods
    fun <T : PolicyState> getHandler(key: PolicyKey<T>): PolicyHandler<T>?
    suspend fun getAllPolicies(): List<Policy<PolicyState>>
    suspend fun getPolicies(category: PolicyCategory): List<Policy<PolicyState>>
    fun isRegistered(key: PolicyKey<*>): Boolean
    fun getComponent(key: PolicyKey<*>): PolicyComponent<out PolicyState>?
    suspend fun getPolicyState(featureName: String): Policy<PolicyState>?
    suspend fun <T : PolicyState> setPolicyState(policyKey: PolicyKey<T>, state: T): ApiResult<Unit>

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