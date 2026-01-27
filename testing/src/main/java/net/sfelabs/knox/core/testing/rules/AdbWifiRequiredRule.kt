package net.sfelabs.knox.core.testing.rules

import android.annotation.SuppressLint
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Annotation to mark tests that require ADB to be connected via WiFi.
 *
 * **IMPORTANT LIMITATIONS:**
 * - This checks if USB debugging is **disabled** in Developer Options, not if ADB is actually connected via WiFi
 * - It cannot detect which ADB transport (USB or WiFi) is currently in use
 * - ADB WiFi connection can be active even when USB debugging is enabled
 * - This is an inverse check: assumes WiFi ADB when USB debugging is disabled
 *
 * **Usage:**
 * To ensure tests run only with WiFi ADB:
 * 1. Connect ADB via WiFi (adb connect <ip>:port or Android 11+ wireless debugging)
 * 2. Disable USB debugging in Developer Options
 * 3. Disconnect any USB cable to ensure USB debugging stays disabled
 *
 * **Common Use Case:**
 * Tests that require WiFi ADB (e.g., to test ethernet functionality while keeping ADB connection)
 * must manually disable USB debugging in settings.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class AdbWifiRequired

/**
 * Rule to skip tests when USB debugging is enabled in Developer Options.
 *
 * See [AdbWifiRequired] for important limitations and usage notes.
 */
class AdbWifiRequiredRule : TestRule {
    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Check if annotation is present on either the test method or test class
                val hasAnnotation = description.getAnnotation(AdbWifiRequired::class.java) != null ||
                    description.testClass?.getAnnotation(AdbWifiRequired::class.java) != null

                if (hasAnnotation) {
                    Assume.assumeTrue("ADB is not connected via WiFi", isAdbWifiConnected())
                }
                statement.evaluate()
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun isAdbWifiConnected(): Boolean {
        return try {
            // Use reflection to access SystemProperties.get() (hidden API)
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)

            // Check sys.usb.config to see if ADB is enabled over USB
            val usbConfig = getMethod.invoke(null, "sys.usb.config", "") as String
            val usbState = getMethod.invoke(null, "sys.usb.state", "") as String

            println("AdbWifiRequiredRule: sys.usb.config = '$usbConfig'")
            println("AdbWifiRequiredRule: sys.usb.state = '$usbState'")

            // If USB config/state does NOT contain "adb", then ADB must be over WiFi
            // (assuming tests are running, which means ADB is connected somehow)
            val hasUsbAdb = usbConfig.contains("adb") || usbState.contains("adb")
            val isWifi = !hasUsbAdb
            println("AdbWifiRequiredRule: hasUsbAdb = $hasUsbAdb, isWifi = $isWifi")

            isWifi
        } catch (e: Exception) {
            println("AdbWifiRequiredRule: Exception accessing property: ${e.message}")
            // If we can't access the property, assume ADB WiFi is not available
            false
        }
    }
}
