package net.sfelabs.knox.core.feature.domain.model

import net.sfelabs.knox.core.feature.api.PolicyKey
import net.sfelabs.knox.core.feature.api.PolicyState
import net.sfelabs.knox.core.feature.api.PolicyStateWrapper

data class Policy<out T : PolicyState> (
    val key: PolicyKey<T>,
    val state: PolicyStateWrapper<T>
)