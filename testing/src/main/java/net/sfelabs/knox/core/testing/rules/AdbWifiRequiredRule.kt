package net.sfelabs.knox.core.testing.rules

import android.annotation.SuppressLint
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class AdbWifiRequired

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
