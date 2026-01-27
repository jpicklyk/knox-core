package net.sfelabs.knox.core.testing.rules

import android.annotation.SuppressLint
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Annotation to mark tests that require ADB to be connected via USB.
 *
 * **IMPORTANT LIMITATIONS:**
 * - This checks if USB debugging is **enabled** in Developer Options, not if ADB is actually connected via USB
 * - It cannot detect which ADB transport (USB or WiFi) is currently in use
 * - USB debugging can be enabled even when ADB is connected via WiFi
 * - Physical USB connection (for charging, ethernet, etc.) doesn't guarantee ADB is using USB transport
 *
 * **Usage:**
 * To ensure tests run only with USB ADB:
 * 1. Connect ADB via USB cable
 * 2. Ensure USB debugging is enabled in Developer Options
 * 3. If also using WiFi ADB, disable USB debugging in Developer Options to force WiFi-only mode
 *
 * **Common Use Case:**
 * Tests that disable WiFi or other network features should use this annotation to avoid losing
 * ADB connection. However, you must manually ensure USB debugging is disabled when using WiFi ADB.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class AdbUsbRequired

/**
 * Rule to skip tests when USB debugging is not enabled in Developer Options.
 *
 * See [AdbUsbRequired] for important limitations and usage notes.
 */
class AdbUsbRequiredRule : TestRule {
    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Check if annotation is present on either the test method or test class
                val hasAnnotation = description.getAnnotation(AdbUsbRequired::class.java) != null ||
                    description.testClass?.getAnnotation(AdbUsbRequired::class.java) != null

                if (hasAnnotation) {
                    Assume.assumeTrue("ADB is not connected via USB", isAdbUsbConnected())
                }
                statement.evaluate()
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun isAdbUsbConnected(): Boolean {
        return try {
            // Use reflection to access SystemProperties.get() (hidden API)
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)

            // Check sys.usb.config to see if ADB is enabled over USB
            val usbConfig = getMethod.invoke(null, "sys.usb.config", "") as String
            val usbState = getMethod.invoke(null, "sys.usb.state", "") as String

            println("AdbUsbRequiredRule: sys.usb.config = '$usbConfig'")
            println("AdbUsbRequiredRule: sys.usb.state = '$usbState'")

            // If USB config/state contains "adb", USB debugging is active
            val hasUsbAdb = usbConfig.contains("adb") || usbState.contains("adb")
            println("AdbUsbRequiredRule: hasUsbAdb = $hasUsbAdb")

            hasUsbAdb
        } catch (e: Exception) {
            println("AdbUsbRequiredRule: Exception accessing property: ${e.message}")
            // If we can't access the property, assume ADB USB is available (default mode)
            true
        }
    }
}
