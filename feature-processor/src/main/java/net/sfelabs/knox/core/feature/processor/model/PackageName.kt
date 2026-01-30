package net.sfelabs.knox.core.feature.processor.model

enum class PackageName(val value: String) {
    FEATURE_PUBLIC("net.sfelabs.knox.core.feature.api"),
    FEATURE_MODEL("net.sfelabs.knox.core.feature.domain.model"),
    FEATURE_REGISTRY("net.sfelabs.knox.core.feature.domain.registry"),
    FEATURE_HANDLER("net.sfelabs.knox.core.feature.domain.usecase.handler"),
    FEATURE_HILT("net.sfelabs.knox.hilt.di"),
    API_DOMAIN_MODEL("net.sfelabs.knox.core.domain.usecase.model")
}