package net.sfelabs.knox.core.feature.api

import net.sfelabs.knox.core.feature.domain.registry.PolicyRegistry

/**
 * Default grouping strategy that groups policies by their primary capability.
 *
 * Policies are grouped based on the first matching MODIFIES_* capability they have.
 * Policies without any matching capability are placed in the "Other" group.
 *
 * This provides a sensible default organization without requiring any configuration.
 */
class CapabilityBasedGroupingStrategy : PolicyGroupingStrategy {

    private val capabilityToGroup = mapOf(
        PolicyCapability.MODIFIES_RADIO to PolicyGroup(
            id = "radio",
            displayName = "Radio & Cellular",
            description = "Policies that modify cellular/radio settings",
            sortOrder = 1
        ),
        PolicyCapability.MODIFIES_WIFI to PolicyGroup(
            id = "wifi",
            displayName = "Wi-Fi",
            description = "Policies that modify Wi-Fi settings",
            sortOrder = 2
        ),
        PolicyCapability.MODIFIES_BLUETOOTH to PolicyGroup(
            id = "bluetooth",
            displayName = "Bluetooth",
            description = "Policies that modify Bluetooth settings",
            sortOrder = 3
        ),
        PolicyCapability.MODIFIES_DISPLAY to PolicyGroup(
            id = "display",
            displayName = "Display",
            description = "Policies that modify display settings",
            sortOrder = 4
        ),
        PolicyCapability.MODIFIES_AUDIO to PolicyGroup(
            id = "audio",
            displayName = "Audio",
            description = "Policies that modify audio settings",
            sortOrder = 5
        ),
        PolicyCapability.MODIFIES_CHARGING to PolicyGroup(
            id = "charging",
            displayName = "Charging",
            description = "Policies that modify charging behavior",
            sortOrder = 6
        ),
        PolicyCapability.MODIFIES_CALLING to PolicyGroup(
            id = "calling",
            displayName = "Calling",
            description = "Policies that modify calling/telephony behavior",
            sortOrder = 7
        ),
        PolicyCapability.MODIFIES_HARDWARE to PolicyGroup(
            id = "hardware",
            displayName = "Hardware",
            description = "Policies that modify hardware components",
            sortOrder = 8
        ),
        PolicyCapability.MODIFIES_BROWSER to PolicyGroup(
            id = "browser",
            displayName = "Browser",
            description = "Policies that modify Samsung Internet browser settings",
            sortOrder = 9
        ),
        PolicyCapability.MODIFIES_SECURITY to PolicyGroup(
            id = "security",
            displayName = "Security",
            description = "Policies that modify security settings",
            sortOrder = 10
        ),
        PolicyCapability.MODIFIES_NETWORK to PolicyGroup(
            id = "network",
            displayName = "Network",
            description = "Policies that modify general network settings",
            sortOrder = 11
        )
    )

    private val otherGroup = PolicyGroup(
        id = "other",
        displayName = "Other",
        description = "Policies that don't fit into other categories",
        sortOrder = 100
    )

    override fun getGroups(): List<PolicyGroup> {
        return capabilityToGroup.values.toList() + otherGroup
    }

    override fun getGroupForPolicy(policy: PolicyComponent<out PolicyState>): PolicyGroup {
        // Find first matching capability-based group
        for ((capability, group) in capabilityToGroup) {
            if (policy.hasCapability(capability)) {
                return group
            }
        }
        return otherGroup
    }

    override fun getPoliciesInGroup(
        groupId: String,
        registry: PolicyRegistry
    ): List<PolicyComponent<out PolicyState>> {
        val capability = capabilityToGroup.entries
            .find { it.value.id == groupId }?.key

        return if (capability != null) {
            registry.getByCapability(capability)
        } else if (groupId == "other") {
            // Policies that don't match any capability group
            registry.getAllComponents().filter { policy ->
                capabilityToGroup.keys.none { policy.hasCapability(it) }
            }
        } else {
            emptyList()
        }
    }
}
