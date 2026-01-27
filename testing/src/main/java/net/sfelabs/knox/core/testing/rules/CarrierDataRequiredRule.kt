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
annotation class CarrierDataRequired

class CarrierDataRequiredRule : TestRule {
    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Check if annotation is present on either the test method or test class
                val hasAnnotation = description.getAnnotation(CarrierDataRequired::class.java) != null ||
                    description.testClass?.getAnnotation(CarrierDataRequired::class.java) != null

                if (hasAnnotation) {
                    Assume.assumeTrue("Carrier data connection is not available", isCarrierDataConnected())
                }
                statement.evaluate()
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun isCarrierDataConnected(): Boolean {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check all networks for an active cellular/carrier data connection
        return connectivityManager.allNetworks.any { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@any false
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return@any false

            // Must have cellular transport AND have an assigned IP address (indicating it's actually connected)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                linkProperties.linkAddresses.isNotEmpty()
        }
    }
}
