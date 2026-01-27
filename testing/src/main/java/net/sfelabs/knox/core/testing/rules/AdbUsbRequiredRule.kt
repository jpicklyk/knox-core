package net.sfelabs.knox.core.testing.rules

import android.annotation.SuppressLint
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class AdbUsbRequired

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
            val port = getMethod.invoke(null, "service.adb.tcp.port", "-1") as String

            println("AdbUsbRequiredRule: service.adb.tcp.port = '$port'")

            // If port is -1 or not set, ADB is USB-only (not over TCP/WiFi)
            val portNumber = port.toIntOrNull() ?: -1
            val isUsb = portNumber <= 0
            println("AdbUsbRequiredRule: portNumber = $portNumber, isUsb = $isUsb")
            isUsb
        } catch (e: Exception) {
            println("AdbUsbRequiredRule: Exception accessing property: ${e.message}")
            // If we can't access the property, assume ADB USB is available (default mode)
            true
        }
    }
}
