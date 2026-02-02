package net.sfelabs.knox.core.feature.api

/**
 * Represents a group of policies for UI display.
 *
 * Groups are defined by customers through [PolicyGroupingStrategy], not by policies themselves.
 * This allows different applications to organize the same policies into different groups
 * based on their specific needs.
 *
 * @param id Unique identifier for this group
 * @param displayName Human-readable name shown in UI
 * @param description Optional description of the group
 * @param iconRes Optional resource ID for an icon (platform-specific)
 * @param sortOrder Order in which groups should be displayed (lower = first)
 */
data class PolicyGroup(
    val id: String,
    val displayName: String,
    val description: String = "",
    val iconRes: Int? = null,
    val sortOrder: Int = 0
)

/**
 * A policy group with its member policies resolved.
 *
 * This is the result of applying a [PolicyGroupingStrategy] to the registry,
 * containing both the group metadata and the actual policy components.
 */
data class ResolvedPolicyGroup(
    val group: PolicyGroup,
    val policies: List<PolicyComponent<out PolicyState>>
) {
    /**
     * Returns true if this group has no policies.
     */
    val isEmpty: Boolean get() = policies.isEmpty()

    /**
     * Returns the number of policies in this group.
     */
    val size: Int get() = policies.size
}
