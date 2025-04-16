package net.sfelabs.knox.core.feature.hilt

import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.feature.api.PolicyCategory
import net.sfelabs.knox.core.feature.api.PolicyComponent
import net.sfelabs.knox.core.feature.api.PolicyKey
import net.sfelabs.knox.core.feature.api.PolicyState
import net.sfelabs.knox.core.feature.data.repository.CachedPolicyRegistry
import net.sfelabs.knox.core.feature.data.repository.DefaultPolicyRegistry
import net.sfelabs.knox.core.feature.domain.registry.PolicyRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HiltPolicyRegistry @Inject constructor() : PolicyRegistry {
    init {
        println("HiltPolicyRegistry being constructed")
    }

    private val delegate = CachedPolicyRegistry(DefaultPolicyRegistry())

    @Inject
    fun setComponents(components: Set<@JvmSuppressWildcards PolicyComponent<out PolicyState>>) {
        delegate.components = components
    }

    override fun <T : PolicyState> getHandler(key: PolicyKey<T>) = delegate.getHandler(key)

    override suspend fun getAllPolicies() = delegate.getAllPolicies()

    override suspend fun getPolicies(category: PolicyCategory) = delegate.getPolicies(category)

    override fun isRegistered(key: PolicyKey<*>) = delegate.isRegistered(key)

    override fun getComponent(key: PolicyKey<*>) = delegate.getComponent(key)

    override suspend fun getPolicyState(featureName: String) = delegate.getPolicyState(featureName)

    override suspend fun <T : PolicyState> setPolicyState(
        policyKey: PolicyKey<T>,
        state: T
    ): ApiResult<Unit> = delegate.setPolicyState(policyKey, state)
}