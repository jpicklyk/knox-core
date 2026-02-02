package net.sfelabs.knox.core.feature.processor.model

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import net.sfelabs.knox.core.feature.api.PolicyCapability
import net.sfelabs.knox.core.feature.api.PolicyCategory

data class ProcessedPolicy(
    val className: String,
    val packageName: String,
    val title: String,
    val description: String,
    val category: PolicyCategory,
    val capabilities: Set<PolicyCapability>,
    val valueType: KSType,
    val configType: KSType?,
    val declaration: KSClassDeclaration
)
