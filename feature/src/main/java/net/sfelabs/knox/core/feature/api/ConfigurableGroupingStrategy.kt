package net.sfelabs.knox.core.feature.api

import net.sfelabs.knox.core.feature.domain.registry.PolicyRegistry

/**
 * Grouping strategy configured via external configuration.
 *
 * This allows non-developers to define groupings without code changes,
 * useful for remote configuration, A/B testing, or customer-specific deployments.
 *
 * ## Example Configuration
 *
 * ```kotlin
 * val config = GroupingConfiguration(
 *     groups = listOf(
 *         PolicyGroup("quick", "Quick Access", sortOrder = 1),
 *         PolicyGroup("advanced", "Advanced", sortOrder = 2)
 *     ),
 *     policyAssignments = mapOf(
 *         "device_lock_mode" to "quick",
 *         "screen_brightness" to "quick",
 *         "band_locking_5g" to "advanced",
 *         "enable_hdm" to "advanced"
 *     )
 * )
 *
 * val strategy = ConfigurableGroupingStrategy(config)
 * ```
 */
class ConfigurableGroupingStrategy(
    private val config: GroupingConfiguration
) : PolicyGroupingStrategy {

    override fun getGroups(): List<PolicyGroup> = config.groups

    override fun getGroupForPolicy(policy: PolicyComponent<out PolicyState>): PolicyGroup? {
        val groupId = config.policyAssignments[policy.policyName] ?: return null
        return config.groups.find { it.id == groupId }
    }

    override fun getPoliciesInGroup(
        groupId: String,
        registry: PolicyRegistry
    ): List<PolicyComponent<out PolicyState>> {
        val policyNames = config.policyAssignments
            .filterValues { it == groupId }
            .keys

        return registry.getAllComponents().filter { it.policyName in policyNames }
    }
}

/**
 * Configuration for [ConfigurableGroupingStrategy].
 *
 * Can be constructed programmatically or parsed from JSON/XML/remote config.
 *
 * @param groups List of groups in display order
 * @param policyAssignments Map of policy name to group ID
 */
data class GroupingConfiguration(
    val groups: List<PolicyGroup>,
    val policyAssignments: Map<String, String>
) {
    /**
     * Builder for creating [GroupingConfiguration] incrementally.
     */
    class Builder {
        private val groups = mutableListOf<PolicyGroup>()
        private val assignments = mutableMapOf<String, String>()

        fun addGroup(group: PolicyGroup): Builder {
            groups.add(group)
            return this
        }

        fun addGroup(
            id: String,
            displayName: String,
            description: String = "",
            sortOrder: Int = groups.size
        ): Builder {
            groups.add(PolicyGroup(id, displayName, description, sortOrder = sortOrder))
            return this
        }

        fun assignPolicy(policyName: String, groupId: String): Builder {
            assignments[policyName] = groupId
            return this
        }

        fun assignPolicies(groupId: String, vararg policyNames: String): Builder {
            policyNames.forEach { assignments[it] = groupId }
            return this
        }

        fun build(): GroupingConfiguration {
            return GroupingConfiguration(
                groups = groups.toList(),
                policyAssignments = assignments.toMap()
            )
        }
    }

    companion object {
        /**
         * Create a new builder for constructing configuration.
         */
        fun builder(): Builder = Builder()
    }
}
