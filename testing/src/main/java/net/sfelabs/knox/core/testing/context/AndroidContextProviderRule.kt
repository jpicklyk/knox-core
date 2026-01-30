package net.sfelabs.knox.core.testing.context

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import net.sfelabs.knox.core.android.AndroidApplicationContextProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit Rule that automatically initializes [AndroidApplicationContextProvider] before each test.
 *
 * This rule eliminates the need to manually set up the context provider in `@Before` methods,
 * reducing boilerplate in test classes that use Knox use cases.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * @RunWith(AndroidJUnit4::class)
 * class MyKnoxTests {
 *
 *     @get:Rule
 *     val contextRule = AndroidContextProviderRule()
 *
 *     @Test
 *     fun testUseCase() = runTest {
 *         // AndroidApplicationContextProvider is already initialized
 *         val result = MyUseCase().invoke(params)
 *         // ...
 *     }
 * }
 * ```
 *
 * ## Accessing the Context
 *
 * The rule provides access to the context if needed:
 *
 * ```kotlin
 * @get:Rule
 * val contextRule = AndroidContextProviderRule()
 *
 * @Test
 * fun testWithContext() {
 *     val context = contextRule.context
 *     // Use context for test setup
 * }
 * ```
 *
 * ## Custom Context Provider
 *
 * You can provide a custom context (e.g., mock) via the constructor:
 *
 * ```kotlin
 * @get:Rule
 * val contextRule = AndroidContextProviderRule { mockContext }
 * ```
 *
 * ## Order with Other Rules
 *
 * If you have multiple rules that depend on context initialization, use `@Rule(order = N)`:
 *
 * ```kotlin
 * @get:Rule(order = 0)
 * val contextRule = AndroidContextProviderRule()
 *
 * @get:Rule(order = 1)
 * val otherRule = SomeOtherRule() // Runs after context is initialized
 * ```
 *
 * @param contextProvider Optional lambda to provide a custom context. If not specified,
 *                        uses [InstrumentationRegistry.getInstrumentation().targetContext].
 *
 * @see TestContextSetup for manual initialization in @Before methods
 * @see AndroidApplicationContextProvider
 */
class AndroidContextProviderRule(
    private val contextProvider: (() -> Context)? = null
) : TestRule {

    private var _context: Context? = null

    /**
     * The context used for this test.
     *
     * @throws IllegalStateException if accessed before the rule has been applied
     */
    val context: Context
        get() = _context
            ?: throw IllegalStateException("Context not yet initialized. Ensure the rule has been applied.")

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                // Initialize context before test
                _context = contextProvider?.invoke()
                    ?: InstrumentationRegistry.getInstrumentation().targetContext

                val provider = object : AndroidApplicationContextProvider {
                    override fun getContext(): Context = _context!!
                }
                AndroidApplicationContextProvider.init(provider)

                try {
                    // Run the test
                    base.evaluate()
                } finally {
                    // Cleanup (context reference cleared but provider remains initialized)
                    _context = null
                }
            }
        }
    }
}
