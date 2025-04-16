package net.sfelabs.knox.core.feature.domain.registry

import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.feature.domain.model.Policy
import net.sfelabs.knox.core.feature.api.PolicyCategory
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler
import net.sfelabs.knox.core.feature.api.PolicyComponent
import net.sfelabs.knox.core.feature.api.PolicyKey
import net.sfelabs.knox.core.feature.api.PolicyState

interface PolicyRegistry {
    fun <T : PolicyState> getHandler(key: PolicyKey<T>): PolicyHandler<T>?
    suspend fun getAllPolicies(): List<Policy<PolicyState>>
    suspend fun getPolicies(category: PolicyCategory): List<Policy<PolicyState>>
    fun isRegistered(key: PolicyKey<*>): Boolean
    fun getComponent(key: PolicyKey<*>): PolicyComponent<out PolicyState>?
    suspend fun getPolicyState(featureName: String): Policy<PolicyState>?
    suspend fun <T : PolicyState> setPolicyState(policyKey: PolicyKey<T>, state: T): ApiResult<Unit>
}