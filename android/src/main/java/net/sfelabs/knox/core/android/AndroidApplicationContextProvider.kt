package net.sfelabs.knox.core.android

import android.content.Context

/**
 * Provides access to the Android Application context throughout the knox-core modules.
 *
 * ## Architecture Decision: Service Locator Pattern
 *
 * This interface uses a service locator pattern with static state rather than constructor
 * injection. While constructor injection is generally preferred, this pattern is used here
 * due to constraints imposed by KSP (Kotlin Symbol Processing) code generation.
 *
 * ### Why Not Constructor Injection?
 *
 * The KSP annotation processor ([ComponentGenerator]) generates policy components at compile
 * time. KSP runs **before** dependency injection frameworks like Hilt, which means:
 *
 * 1. Generated code cannot receive dependencies via constructor injection
 * 2. KSP generates empty constructor calls: `PolicyClass()`
 * 3. The generated Hilt modules cannot know what dependencies to inject
 *
 * The dependency chain is:
 * ```
 * KSP generates: XxxComponent() → instantiates XxxPolicy() → instantiates XxxUseCase()
 * ```
 *
 * All three must have empty constructors because KSP generates static code.
 *
 * ### Memory Safety
 *
 * This pattern is **memory-safe** because:
 * - Only the Application context is stored (not Activity context)
 * - Application context lives for the entire process lifetime
 * - No risk of Activity/Fragment memory leaks
 *
 * ### Testing
 *
 * For tests, initialize with a test provider before running:
 * ```kotlin
 * @Before
 * fun setup() {
 *     val testProvider = object : AndroidApplicationContextProvider {
 *         override fun getContext() = ApplicationProvider.getApplicationContext()
 *     }
 *     AndroidApplicationContextProvider.init(testProvider)
 * }
 * ```
 *
 * ### Future Improvements
 *
 * See GitHub issue for a proposed DI-agnostic architecture that would support
 * Hilt, Koin, or no DI framework while maintaining KSP compatibility.
 *
 * @see WithAndroidApplicationContext
 * @see net.sfelabs.knox.core.feature.processor.generator.ComponentGenerator
 */
interface AndroidApplicationContextProvider {
    /**
     * Returns the Application context.
     *
     * Implementations must return [Context.getApplicationContext] to ensure
     * memory safety and avoid leaking Activity references.
     */
    fun getContext(): Context

    companion object {
        private var instance: AndroidApplicationContextProvider? = null

        /**
         * Initializes the global provider instance.
         *
         * This must be called once during application startup, typically in
         * [android.app.Application.onCreate]:
         *
         * ```kotlin
         * @HiltAndroidApp
         * class MyApplication : Application() {
         *     @Inject
         *     lateinit var contextProvider: AndroidApplicationContextProvider
         *
         *     override fun onCreate() {
         *         super.onCreate()
         *         AndroidApplicationContextProvider.init(contextProvider)
         *     }
         * }
         * ```
         *
         * @param provider The provider implementation (typically injected by Hilt)
         */
        fun init(provider: AndroidApplicationContextProvider) {
            instance = provider
        }

        /**
         * Returns the Application context from the initialized provider.
         *
         * @throws IllegalStateException if [init] has not been called
         */
        fun get(): Context = instance?.getContext()
            ?: throw IllegalStateException("AndroidApplicationContextProvider not initialized")
    }
}