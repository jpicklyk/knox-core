package net.sfelabs.knox.core.testing.rules

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.fail
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Marks a test (or every test in a class) as requiring a powered-on cellular radio.
 *
 * Use together with [RadioRequiredRule]. Modem commands such as band locking fail with an
 * opaque Knox status code when the radio is off, which is the default state on Tactical
 * Edition firmware (airplane mode enabled out of the box) and is also left behind by
 * tactical device mode.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RadioRequired

/**
 * Ensures the cellular radio is powered on before a [RadioRequired] test runs.
 *
 * Unlike the precondition rules that skip a test via `Assume`, this rule actively fixes the
 * device state: if airplane mode is on it is disabled through the shell, then the rule waits
 * up to [timeoutMs] for the telephony stack to report the radio powered on. If the radio does
 * not come up the test fails with a message naming the cause.
 *
 * Airplane mode is intentionally left off afterwards; radio tests generally need it that way.
 *
 * The test app is not granted READ_PHONE_STATE at runtime, so the radio state is read from the
 * telephony registry dump over the shell rather than from [android.telephony.TelephonyManager].
 */
class RadioRequiredRule(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : TestRule {

    override fun apply(statement: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val hasAnnotation = description.getAnnotation(RadioRequired::class.java) != null ||
                    description.testClass?.getAnnotation(RadioRequired::class.java) != null

                if (hasAnnotation) {
                    ensureRadioIsPoweredOn()
                }
                statement.evaluate()
            }
        }
    }

    private fun ensureRadioIsPoweredOn() {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (isAirplaneModeOn()) {
            Log.i(TAG, "Airplane mode is on; disabling it so the modem accepts radio commands")
            uiDevice.executeShellCommand("cmd connectivity airplane-mode disable")
        }

        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!isAirplaneModeOn() && isRadioPoweredOn(uiDevice)) {
                return
            }
            SystemClock.sleep(pollIntervalMs)
        }
        fail(
            "Radio is not powered on after ${timeoutMs / 1000}s " +
                "(airplane mode on or modem not ready). This test requires an active modem."
        )
    }

    private fun isAirplaneModeOn(): Boolean {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }

    private fun isRadioPoweredOn(uiDevice: UiDevice): Boolean {
        val dump = uiDevice.executeShellCommand("dumpsys telephony.registry")
        return RADIO_POWERED_ON.containsMatchIn(dump)
    }

    companion object {
        private const val TAG = "RadioRequiredRule"
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val DEFAULT_POLL_INTERVAL_MS = 1_000L

        // TelephonyRegistry dumps RadioPowerState: 0 = off, 1 = on, 2 = unavailable.
        private val RADIO_POWERED_ON = Regex("mRadioPowerState=1\\b")
    }
}
