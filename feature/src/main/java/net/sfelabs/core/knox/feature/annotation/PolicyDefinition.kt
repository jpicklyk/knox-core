package net.sfelabs.knox.core.feature.annotation

import net.sfelabs.knox.core.feature.api.PolicyCategory

@Target(AnnotationTarget.CLASS)
annotation class PolicyDefinition(
    val title: String,
    val description: String,
    val category: PolicyCategory
)
