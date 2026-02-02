package net.sfelabs.knox.core.feature.annotation

import net.sfelabs.knox.core.feature.api.PolicyCapability
import net.sfelabs.knox.core.feature.api.PolicyCategory

/**
 * Marks a class as a policy definition, enabling automatic generation of
 * PolicyComponent, PolicyKey, and registration code.
 *
 * @param title Display title for the policy in UI
 * @param description Human-readable description of what the policy does
 * @param category The category type for UI organization (Toggle, ConfigurableToggle, etc.)
 * @param capabilities Intrinsic capabilities that describe what the policy modifies
 *        and its device requirements. Used for filtering and grouping.
 */
@Target(AnnotationTarget.CLASS)
annotation class PolicyDefinition(
    val title: String,
    val description: String,
    val category: PolicyCategory,
    val capabilities: Array<PolicyCapability> = []
)
