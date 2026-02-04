package net.sfelabs.knox.core.feature.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

class CachedPolicyRegistry(private val delegate: DefaultPolicyRegistry) : PolicyRegistry {
    private val cache = mutableMapOf<String, Policy<PolicyState>>()
    private val mutex = Mutex()

    var components: Set<PolicyComponent<out PolicyState>>
        get() = delegate.components
        set(value) {
            delegate.components = value
            cache.clear()
        }

    suspend fun getFeature(featureName: String, forceRefresh: Boolean = false): Policy<PolicyState>? {
        return mutex.withLock {
            when {
                forceRefresh -> delegate.getPolicyState(featureName)?.also { cache[featureName] = it }
                featureName in cache -> cache[featureName]
                else -> delegate.getPolicyState(featureName)?.also { cache[featureName] = it }
            }
        }
    }

    override suspend fun getAllPolicies(): List<Policy<*>> = mutex.withLock {
        delegate.getAllPolicies().also { features ->
            cache.clear()
            features.forEach {
                cache[it.key.policyName] = it
            }
        }
    }

    suspend fun clearCache() = mutex.withLock { cache.clear() }

    override suspend fun getPolicyState(featureName: String) = getFeature(featureName, false)
    override suspend fun <T : PolicyState> setPolicyState(
        policyKey: PolicyKey<T>,
        state: T
    ): ApiResult<Unit> {
        return mutex.withLock {
            getHandler(policyKey)?.setState(state)?.also { result ->
                if (result is ApiResult.Success) {
                    // Invalidate cache entry so next getPolicyState fetches fresh from device.
                    // We cannot cache the input state because it may be missing device-derived
                    // fields (e.g., HdmState.supportedMask) that are only populated by getState().
                    cache.remove(policyKey.policyName)
                }
            } ?: ApiResult.Error(DefaultApiError.UnexpectedError("Policy handler not found"))
        }
    }

    override suspend fun <T : PolicyState> setAndRefreshPolicyState(
        policyKey: PolicyKey<T>,
        state: T
    ): ApiResult<Policy<T>> = mutex.withLock {
        // 1. Set the state
        val setResult = getHandler(policyKey)?.setState(state)
            ?: return@withLock ApiResult.Error(
                DefaultApiError.UnexpectedError("Policy handler not found")
            )

        if (setResult is ApiResult.Error) {
            return@withLock ApiResult.Error(setResult.apiError, setResult.exception)
        }
        if (setResult is ApiResult.NotSupported) {
            return@withLock ApiResult.NotSupported
        }

        // 2. Refresh from device to get complete state including device-derived fields
        val handler = getHandler(policyKey)
            ?: return@withLock ApiResult.Error(
                DefaultApiError.UnexpectedError("Policy handler not found after successful set")
            )

        return@withLock try {
            val refreshedState = handler.getState()
            @Suppress("UNCHECKED_CAST")
            val policy = Policy(policyKey, PolicyStateWrapper(refreshedState as T))

            // 3. Update cache with the complete refreshed state
            cache[policyKey.policyName] = policy as Policy<PolicyState>

            ApiResult.Success(policy)
        } catch (e: Exception) {
            // Clear stale cache entry on refresh failure
            cache.remove(policyKey.policyName)
            ApiResult.Error(
                DefaultApiError.UnexpectedError("Policy set succeeded but refresh failed: ${e.message}"),
                e
            )
        }
    }

    override fun getComponent(key: PolicyKey<*>) = delegate.getComponent(key)
    override fun <T : PolicyState> getHandler(key: PolicyKey<T>) = delegate.getHandler(key)
    override suspend fun getPolicies(category: PolicyCategory) = delegate.getPolicies(category)
    override fun isRegistered(key: PolicyKey<*>) = delegate.isRegistered(key)

    // Capability-based query delegations
    override fun getByCapability(capability: PolicyCapability) = delegate.getByCapability(capability)
    override fun getByCapabilities(capabilities: Set<PolicyCapability>, matchAll: Boolean) =
        delegate.getByCapabilities(capabilities, matchAll)
    override fun getByCategory(category: PolicyCategory) = delegate.getByCategory(category)
    override fun query(
        category: PolicyCategory?,
        capabilities: Set<PolicyCapability>?,
        matchAllCapabilities: Boolean
    ) = delegate.query(category, capabilities, matchAllCapabilities)
    override fun getAllComponents() = delegate.getAllComponents()
}