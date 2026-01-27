package net.sfelabs.knox.core.testing.rules

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class EthernetRequired

class EthernetRequiredRule : TestRule {
    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Check if annotation is present on either the test method or test class
                val hasAnnotation = description.getAnnotation(EthernetRequired::class.java) != null ||
                    description.testClass?.getAnnotation(EthernetRequired::class.java) != null

                if (hasAnnotation) {
                    Assume.assumeTrue("Ethernet connection is not available", isEthernetConnected())
                }
                statement.evaluate()
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun isEthernetConnected(): Boolean {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check all networks, not just the active one
        // Note: getAllNetworks() is deprecated but appropriate for one-time synchronous checks
        return connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        }
    }
}