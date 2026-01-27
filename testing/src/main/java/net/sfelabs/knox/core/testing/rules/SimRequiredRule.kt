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
annotation class SimRequired

class SimRequiredRule : TestRule {
    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Check if annotation is present on either the test method or test class
                val hasAnnotation = description.getAnnotation(SimRequired::class.java) != null ||
                    description.testClass?.getAnnotation(SimRequired::class.java) != null

                if (hasAnnotation) {
                    Assume.assumeTrue("SIM card is not present", isSimCardPresent(ApplicationProvider.getApplicationContext()))
                }
                statement.evaluate()
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun isSimCardPresent(context: Context = ApplicationProvider.getApplicationContext()): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Check all physical SIM slots
        val phoneCount = telephonyManager.phoneCount
        for (slotIndex in 0 until phoneCount) {
            val simState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                telephonyManager.getSimState(slotIndex)
            } else {
                telephonyManager.simState
            }

            when (simState) {
                TelephonyManager.SIM_STATE_READY,
                TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                TelephonyManager.SIM_STATE_PIN_REQUIRED,
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> return true
            }
        }

        return false
    }
}