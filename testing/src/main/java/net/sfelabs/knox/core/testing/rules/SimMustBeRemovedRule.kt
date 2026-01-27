package net.sfelabs.knox.core.testing.rules

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class SimRemoved

class SimMustBeRemovedRule : TestRule {
    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Check if annotation is present on either the test method or test class
                val hasAnnotation = description.getAnnotation(SimRemoved::class.java) != null ||
                    description.testClass?.getAnnotation(SimRemoved::class.java) != null

                if (hasAnnotation) {
                    Assume.assumeFalse("SIM card is present, but it should be removed", isSimCardPresent(ApplicationProvider.getApplicationContext()))
                }
                statement.evaluate()
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun isSimCardPresent(context: Context = ApplicationProvider.getApplicationContext()): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // For Android 5.1+ (API 22+), check all SIM slots using SubscriptionManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                if (subscriptionManager != null) {
                    val activeSubscriptions = subscriptionManager.activeSubscriptionInfoCount
                    // If there are active subscriptions, there's at least one SIM
                    if (activeSubscriptions > 0) {
                        return true
                    }
                }
            } catch (e: Exception) {
                // Fall back to checking telephony manager
            }
        }

        // Fallback: Check default SIM state for single-SIM or older devices
        return when (telephonyManager.simState) {
            TelephonyManager.SIM_STATE_READY -> true
            TelephonyManager.SIM_STATE_ABSENT -> false
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> true
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> true
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> true
            TelephonyManager.SIM_STATE_UNKNOWN -> false
            else -> false
        }
    }
}