package net.sfelabs.knox.core.feature.api

import net.sfelabs.knox.core.feature.domain.registry.PolicyRegistry

/**
 * Strategy for grouping policies into UI categories.
 *
 * This interface allows customers to define custom groupings for their application
 * without modifying policies or the framework. The framework provides
 * [CapabilityBasedGroupingStrategy] as a default implementation.
 *
 * ## Example: Custom Grouping Strategy
 *
 * ```kotlin
 * class FieldOpsGroupingStrategy : PolicyGroupingStrategy {
 *     private val groups = listOf(
 *         PolicyGroup("quick", "Quick Toggles", sortOrder = 1),
 *         PolicyGroup("radio", "Radio Settings", sortOrder = 2),
 *         PolicyGroup("advanced", "Advanced", sortOrder = 3)
 *     )
 *
 *     private val groupMappings = mapOf(
 *         "quick" to setOf("tactical_device_mode", "enable_night_vision_mode"),
 *         "radio" to setOf("band_locking_5g", "band_locking_lte", "nr_mode"),
 *         "advanced" to setOf("enable_hdm", "disable_ims")
 *     )
 *
 *     override fun getGroups() = groups
 *
 *     override fun getGroupForPolicy(policy: PolicyComponent<*>): PolicyGroup? {
 *         return groupMappings.entries
 *             .find { policy.policyName in it.value }
 *             ?.let { entry -> groups.find { it.id == entry.key } }
 *     }
 *
 *     override fun getPoliciesInGroup(groupId: String, registry: PolicyRegistry): List<PolicyComponent<*>> {
 *         val policyNames = groupMappings[groupId] ?: return emptyList()
 *         return registry.getAllComponents().filter { it.policyName in policyNames }
 *     }
 * }
 * ```
 */
interface PolicyGroupingStrategy {
    /**
     * Get all defined groups in display order.
     */
    fun getGroups(): List<PolicyGroup>

    /**
     * Get the group that a policy belongs to.
     *
     * @return The group for this policy, or null if it doesn't belong to any group
     */
    fun getGroupForPolicy(policy: PolicyComponent<out PolicyState>): PolicyGroup?

    /**
     * Get all policies in a specific group.
     *
     * @param groupId The group identifier
     * @param registry The policy registry to query
     * @return List of policy components in the group
     */
    fun getPoliciesInGroup(groupId: String, registry: PolicyRegistry): List<PolicyComponent<out PolicyState>>

    /**
     * Get all groups with their policies resolved.
     *
     * @param registry The policy registry to query
     * @param includeEmpty If false, groups with no policies are omitted
     * @return List of resolved groups in display order
     */
    fun resolveAllGroups(
        registry: PolicyRegistry,
        includeEmpty: Boolean = false
    ): List<ResolvedPolicyGroup> {
        return getGroups()
            .map { group ->
                ResolvedPolicyGroup(
                    group = group,
                    policies = getPoliciesInGroup(group.id, registry)
                )
            }
            .filter { includeEmpty || it.policies.isNotEmpty() }
            .sortedBy { it.group.sortOrder }
    }
}
