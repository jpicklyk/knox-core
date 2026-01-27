package net.sfelabs.knox.core.testing.rules

import android.content.Context
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

    fun isSimCardPresent(context: Context = ApplicationProvider.getApplicationContext()): Boolean {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

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