package net.sfelabs.knox.core.feature.api

interface PolicyDescriptor<T: PolicyState> {
    val key: PolicyKey<T>
    val component: PolicyComponent<T>
}