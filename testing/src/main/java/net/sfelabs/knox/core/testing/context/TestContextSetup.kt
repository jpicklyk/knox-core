package net.sfelabs.knox.core.testing.context

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import net.sfelabs.knox.core.android.AndroidApplicationContextProvider

/**
 * Helper object for initializing [AndroidApplicationContextProvider] in tests.
 *
 * This eliminates the boilerplate of creating an anonymous provider implementation
 * in every test class.
 *
 * ## Usage in @Before
 *
 * ```kotlin
 * @Before
 * fun setup() {
 *     TestContextSetup.initFromInstrumentation()
 * }
 * ```
 *
 * ## Usage with Custom Context
 *
 * ```kotlin
 * @Before
 * fun setup() {
 *     val mockContext = mockk<Context>(relaxed = true)
 *     TestContextSetup.init(mockContext)
 * }
 * ```
 *
 * ## Alternative: Use AndroidContextProviderRule
 *
 * For automatic setup/teardown, consider using [AndroidContextProviderRule] instead:
 *
 * ```kotlin
 * @get:Rule
 * val contextRule = AndroidContextProviderRule()
 * ```
 *
 * @see AndroidContextProviderRule
 * @see AndroidApplicationContextProvider
 */
object TestContextSetup {

    /**
     * Initializes [AndroidApplicationContextProvider] with the instrumentation target context.
     *
     * This is the most common setup for Android instrumentation tests:
     *
     * ```kotlin
     * @Before
     * fun setup() {
     *     TestContextSetup.initFromInstrumentation()
     * }
     * ```
     *
     * @return The context that was used for initialization (useful for assertions)
     */
    fun initFromInstrumentation(): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        init(context)
        return context
    }

    /**
     * Initializes [AndroidApplicationContextProvider] with a custom context.
     *
     * Use this when you need to provide a mock or fake context:
     *
     * ```kotlin
     * @Before
     * fun setup() {
     *     val mockContext = mockk<Context>(relaxed = true)
     *     TestContextSetup.init(mockContext)
     * }
     * ```
     *
     * @param context The context to use (typically Application context or mock)
     */
    fun init(context: Context) {
        val provider = object : AndroidApplicationContextProvider {
            override fun getContext(): Context = context
        }
        AndroidApplicationContextProvider.init(provider)
    }

    /**
     * Creates an [AndroidApplicationContextProvider] from a context without initializing it.
     *
     * Useful when you need the provider instance for other purposes:
     *
     * ```kotlin
     * val provider = TestContextSetup.createProvider(context)
     * // Use provider directly or initialize later
     * AndroidApplicationContextProvider.init(provider)
     * ```
     *
     * @param context The context to wrap
     * @return An [AndroidApplicationContextProvider] that returns the given context
     */
    fun createProvider(context: Context): AndroidApplicationContextProvider {
        return object : AndroidApplicationContextProvider {
            override fun getContext(): Context = context
        }
    }
}
