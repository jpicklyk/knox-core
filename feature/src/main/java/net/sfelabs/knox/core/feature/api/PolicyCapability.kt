package net.sfelabs.knox.core.feature.api

/**
 * Intrinsic capabilities and requirements of a policy.
 *
 * These are factual properties about what the policy does to the device,
 * not presentation/grouping preferences or SDK dependencies.
 *
 * Capabilities describe device/Android concepts (what the policy modifies),
 * device requirements (what hardware is needed), and impact characteristics.
 *
 * **Note:** SDK requirements are NOT capabilities.
 * They are implicit based on which module contains the policy. This keeps knox-core
 * SDK-agnostic while allowing powerful capability-based filtering and grouping.
 *
 * ## Usage
 *
 * Capabilities are declared in the `@PolicyDefinition` annotation:
 *
 * ```kotlin
 * @PolicyDefinition(
 *     title = "5G Band Locking",
 *     description = "...",
 *     category = PolicyCategory.ConfigurableToggle,
 *     capabilities = [
 *         PolicyCapability.REQUIRES_SIM,
 *         PolicyCapability.MODIFIES_RADIO,
 *         PolicyCapability.AFFECTS_CONNECTIVITY
 *     ]
 * )
 * class BandLocking5gPolicy : ConfigurableStatePolicy<...>()
 * ```
 *
 * ## Querying
 *
 * Use the registry's capability-based query methods:
 *
 * ```kotlin
 * // Get all policies that modify radio settings
 * val radioPolicies = registry.getByCapability(PolicyCapability.MODIFIES_RADIO)
 *
 * // Get all policies that require SIM and affect connectivity
 * val connectivityPolicies = registry.getByCapabilities(
 *     setOf(PolicyCapability.REQUIRES_SIM, PolicyCapability.AFFECTS_CONNECTIVITY),
 *     matchAll = true
 * )
 * ```
 */
enum class PolicyCapability {
    // What the policy modifies (device/Android concepts)

    /** Policy modifies cellular/radio settings (bands, modes, etc.) */
    MODIFIES_RADIO,

    /** Policy modifies Wi-Fi settings */
    MODIFIES_WIFI,

    /** Policy modifies Bluetooth settings */
    MODIFIES_BLUETOOTH,

    /** Policy modifies display settings (brightness, color, etc.) */
    MODIFIES_DISPLAY,

    /** Policy modifies audio settings */
    MODIFIES_AUDIO,

    /** Policy modifies charging behavior */
    MODIFIES_CHARGING,

    /** Policy modifies calling/telephony behavior */
    MODIFIES_CALLING,

    /** Policy modifies hardware components (sensors, camera, etc.) */
    MODIFIES_HARDWARE,

    /** Policy modifies security settings */
    MODIFIES_SECURITY,

    /** Policy modifies general network settings */
    MODIFIES_NETWORK,

    /** Policy modifies browser settings (Samsung Internet) */
    MODIFIES_BROWSER,

    // Device requirements

    /** Policy requires a SIM card to be present */
    REQUIRES_SIM,

    /** Policy requires Hardware Device Mode (HDM) */
    REQUIRES_HDM,

    /** Policy requires dual SIM support */
    REQUIRES_DUAL_SIM,

    // Impact characteristics

    /** Policy affects security-sensitive functionality */
    SECURITY_SENSITIVE,

    /** Policy affects device connectivity */
    AFFECTS_CONNECTIVITY,

    /** Policy may affect battery life */
    AFFECTS_BATTERY,

    /** Policy requires device reboot to take effect */
    REQUIRES_REBOOT,

    /** Policy settings persist across device reboot */
    PERSISTENT_ACROSS_REBOOT,

    // Compliance frameworks

    /** Policy is relevant to STIG (Security Technical Implementation Guide) compliance */
    STIG
}
