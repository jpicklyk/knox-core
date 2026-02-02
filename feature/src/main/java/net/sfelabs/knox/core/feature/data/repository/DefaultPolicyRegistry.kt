package net.sfelabs.knox.core.feature.data.repository

import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.domain.usecase.model.DefaultApiError
import net.sfelabs.knox.core.feature.api.PolicyCapability
import net.sfelabs.knox.core.feature.api.PolicyCategory
import net.sfelabs.knox.core.feature.api.PolicyComponent
import net.sfelabs.knox.core.feature.api.PolicyKey
import net.sfelabs.knox.core.feature.api.PolicyState
import net.sfelabs.knox.core.feature.api.PolicyStateWrapper
import net.sfelabs.knox.core.feature.domain.model.Policy
import net.sfelabs.knox.core.feature.domain.registry.PolicyRegistry
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler

/**
 * Default implementation of PolicyRegistry with indexed lookups for O(1) queries.
 *
 * Indexes are rebuilt when components are set, enabling efficient querying by:
 * - Policy name (exact match)
 * - Category
 * - Capability
 */
class DefaultPolicyRegistry : PolicyRegistry {
    var components: Set<PolicyComponent<out PolicyState>> = emptySet()
        set(value) {
            field = value
            rebuildIndexes()
        }

    // Indexes for O(1) lookups
    private var componentsByName: Map<String, PolicyComponent<out PolicyState>> = emptyMap()
    private var componentsByCategory: Map<PolicyCategory, List<PolicyComponent<out PolicyState>>> = emptyMap()
    private var componentsByCapability: Map<PolicyCapability, List<PolicyComponent<out PolicyState>>> = emptyMap()

    private fun rebuildIndexes() {
        componentsByName = components.associateBy { it.policyName }
        componentsByCategory = components.groupBy { it.category }
        componentsByCapability = PolicyCapability.entries.associateWith { cap ->
            components.filter { it.hasCapability(cap) }
        }
    }

    override fun getComponent(key: PolicyKey<*>): PolicyComponent<out PolicyState>? {
        return componentsByName[key.policyName]
    }

    override fun <T : PolicyState> getHandler(key: PolicyKey<T>): PolicyHandler<T>? {
        val component = componentsByName[key.policyName] ?: return null

        return if (component.key::class == key::class) {
            @Suppress("UNCHECKED_CAST")
            component.handler as? PolicyHandler<T>
        } else {
            null
        }
    }

    override suspend fun getAllPolicies(): List<Policy<*>> {
        return components.map { component ->
            @Suppress("UNCHECKED_CAST")
            val typedComponent = component
            val handler = typedComponent.handler
            Policy(
                key = typedComponent.key,
                state = PolicyStateWrapper(handler.getState())
            )
        }
    }

    override suspend fun getPolicies(category: PolicyCategory): List<Policy<*>> {
        return components
            .filter { it.category == category }
            .map { component ->
                @Suppress("UNCHECKED_CAST")
                val typedComponent = component
                val handler = typedComponent.handler
                Policy(
                    key = typedComponent.key,
                    state = PolicyStateWrapper(handler.getState())
                )
            }
    }

    override fun isRegistered(key: PolicyKey<*>): Boolean {
        return componentsByName.containsKey(key.policyName)
    }

    override suspend fun getPolicyState(featureName: String): Policy<PolicyState>? {
        val component = componentsByName[featureName] ?: return null
        @Suppress("UNCHECKED_CAST")
        val handler = component.handler
        return Policy(
            key = component.key,
            state = PolicyStateWrapper(handler.getState())
        )
    }

    override suspend fun <T : PolicyState> setPolicyState(
        policyKey: PolicyKey<T>,
        state: T
    ): ApiResult<Unit> {
        return getHandler(policyKey)?.setState(state)
            ?: ApiResult.Error(DefaultApiError.UnexpectedError("Policy handler not found"))
    }

    // Capability-based query implementations

    override fun getByCapability(capability: PolicyCapability): List<PolicyComponent<out PolicyState>> {
        return componentsByCapability[capability] ?: emptyList()
    }

    override fun getByCapabilities(
        capabilities: Set<PolicyCapability>,
        matchAll: Boolean
    ): List<PolicyComponent<out PolicyState>> {
        if (capabilities.isEmpty()) return components.toList()

        return if (matchAll) {
            components.filter { it.hasAllCapabilities(capabilities) }
        } else {
            // Union all capability indexes to find policies with ANY of the capabilities
            capabilities
                .flatMap { cap -> componentsByCapability[cap] ?: emptyList() }
                .distinct()
        }
    }

    override fun getByCategory(category: PolicyCategory): List<PolicyComponent<out PolicyState>> {
        return componentsByCategory[category] ?: emptyList()
    }

    override fun query(
        category: PolicyCategory?,
        capabilities: Set<PolicyCapability>?,
        matchAllCapabilities: Boolean
    ): List<PolicyComponent<out PolicyState>> {
        // Start with category filter if provided (uses index)
        var result: List<PolicyComponent<out PolicyState>> = if (category != null) {
            componentsByCategory[category] ?: emptyList()
        } else {
            components.toList()
        }

        // Apply capability filter if provided
        if (!capabilities.isNullOrEmpty()) {
            result = result.filter { component ->
                if (matchAllCapabilities) {
                    component.hasAllCapabilities(capabilities)
                } else {
                    component.hasAnyCapability(capabilities)
                }
            }
        }

        return result
    }

    override fun getAllComponents(): List<PolicyComponent<out PolicyState>> {
        return components.toList()
    }
}