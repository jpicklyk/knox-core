package net.sfelabs.knox.core.testing.rules

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

    private fun isAdbWifiConnected(): Boolean {
        return try {
            // Use reflection to access SystemProperties.get() (hidden API)
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
            val port = getMethod.invoke(null, "service.adb.tcp.port", "-1") as String

            // If port is a positive number, ADB over TCP/WiFi is enabled
            port.toIntOrNull()?.let { it > 0 } ?: false
        } catch (e: Exception) {
            // If we can't access the property, assume ADB WiFi is not available
            false
        }
    }
}
