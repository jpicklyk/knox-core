package net.sfelabs.knox.core.feature.processor.generator

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import net.sfelabs.knox.core.domain.usecase.model.ApiResult
import net.sfelabs.knox.core.feature.api.PolicyCategory
import net.sfelabs.knox.core.feature.api.PolicyComponent
import net.sfelabs.knox.core.feature.api.PolicyKey
import net.sfelabs.knox.core.feature.api.PolicyParameters
import net.sfelabs.knox.core.feature.domain.usecase.handler.PolicyHandler
import net.sfelabs.knox.core.feature.processor.model.ProcessedPolicy
import net.sfelabs.knox.core.feature.processor.utils.GeneratedPackages
import net.sfelabs.knox.core.feature.processor.utils.NameUtils.classNameToPolicyName
import net.sfelabs.knox.core.feature.processor.utils.toClassName

/**
 * Generates [PolicyComponent] wrapper classes for policies annotated with `@PolicyDefinition`.
 *
 * ## Generated Code Structure
 *
 * For each annotated policy class, this generator creates a component class:
 *
 * ```kotlin
 * // Input: @PolicyDefinition class AutoCallPickupPolicy
 * // Output:
 * class AutoCallPickupPolicyComponent : PolicyComponent<AutoCallPickupState> {
 *     private val policyImpl = AutoCallPickupPolicy()
 *     override val policyName = "auto-call-pickup"
 *     override val handler = object : PolicyHandler<AutoCallPickupState> { ... }
 *     // ... other properties
 * }
 * ```
 *
 * ## Empty Constructor Constraint
 *
 * **Important:** Generated components instantiate policies using empty constructors (`%T()`).
 * This is a deliberate architectural decision, not an oversight.
 *
 * ### Why Empty Constructors?
 *
 * KSP (Kotlin Symbol Processing) runs at compile time, **before** dependency injection
 * frameworks like Hilt process their annotations. This creates a fundamental constraint:
 *
 * 1. KSP cannot access Hilt's dependency graph (it doesn't exist yet)
 * 2. KSP cannot know what dependencies a class needs at runtime
 * 3. Generated code must be statically valid without DI framework involvement
 *
 * ### The Processing Order
 *
 * ```
 * 1. KSP runs         → Generates XxxComponent with `XxxPolicy()`
 * 2. Hilt runs        → Processes @Module, @Provides annotations
 * 3. Compile completes → Both generated sources are compiled
 * 4. Runtime          → Hilt creates dependency graph, provides components
 * ```
 *
 * ### How Policies Access Dependencies
 *
 * Since policies cannot receive dependencies via constructor, they use the service locator
 * pattern via [WithAndroidApplicationContext]:
 *
 * ```kotlin
 * class MyPolicy : BooleanStatePolicy() {
 *     // Use case instantiated with empty constructor
 *     private val useCase = MyUseCase()
 * }
 *
 * class MyUseCase : WithAndroidApplicationContext, SuspendingUseCase<...>() {
 *     override suspend fun execute(params: Params): ApiResult<...> {
 *         // Access context via the interface
 *         val service = applicationContext.getSystemService(...)
 *     }
 * }
 * ```
 *
 * ### Future Improvements
 *
 * A DI-agnostic architecture has been proposed that would:
 * - Generate components with `ContextProvider` constructor parameters
 * - Generate a factory instead of Hilt-specific modules
 * - Support Hilt, Koin, or no DI framework
 *
 * See the GitHub issue on knox-core for details.
 *
 * @see net.sfelabs.knox.core.android.AndroidApplicationContextProvider
 * @see net.sfelabs.knox.core.android.WithAndroidApplicationContext
 */
class ComponentGenerator(
    private val environment: SymbolProcessorEnvironment
) {
    fun generate(policies: List<ProcessedPolicy>) {
        policies.forEach { policy ->
            generateComponent(policy)
        }
    }

    private fun generateComponent(policy: ProcessedPolicy) {
        val componentSpec = TypeSpec.classBuilder("${policy.className}Component")
            .addSuperinterface(
                ClassName.bestGuess(PolicyComponent::class.qualifiedName!!)
                    .parameterizedBy(policy.valueType.toClassName())
            )
            .addProperty(
                // Note: Empty constructor call is intentional. See class KDoc for explanation.
                PropertySpec.builder("policyImpl", ClassName(policy.packageName, policy.className))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T()", ClassName(policy.packageName, policy.className))
                    .build()
            )
            .addProperties(generateComponentProperties(policy))
            .build()

        writeToFile(componentSpec, policy)
    }

    private fun generateComponentProperties(policy: ProcessedPolicy): List<PropertySpec> {
        val stateType = policy.valueType.toClassName()

        return listOf(
            PropertySpec.builder("policyName", String::class)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("%S", classNameToPolicyName(policy.className))
                .build(),

            PropertySpec.builder("title", String::class)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("%S", policy.title)
                .build(),

            PropertySpec.builder("description", String::class)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("%S", policy.description)
                .build(),

            PropertySpec.builder("category", PolicyCategory::class)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("%T.%L", PolicyCategory::class, policy.category.name)
                .build(),

            PropertySpec.builder("handler",
                ClassName.bestGuess(PolicyHandler::class.qualifiedName!!)
                    .parameterizedBy(stateType)  // Use consistent type
            )
                .addModifiers(KModifier.OVERRIDE)
                .initializer(buildHandlerInitializer(policy))
                .build(),

            PropertySpec.builder("defaultValue", stateType)  // Use consistent type
                .addModifiers(KModifier.OVERRIDE)
                .initializer("policyImpl.defaultValue")
                .build(),

            PropertySpec.builder("key",
                ClassName.bestGuess(PolicyKey::class.qualifiedName!!)
                    .parameterizedBy(stateType)  // Use consistent type
            )
                .addModifiers(KModifier.OVERRIDE)
                .initializer(
                    "%T",
                    ClassName(getGeneratedPackage(), "${policy.className}Key")
                )
                .build()
        )
    }

    private fun buildHandlerInitializer(policy: ProcessedPolicy): CodeBlock {
        val stateType = policy.valueType.toClassName()

        return CodeBlock.builder()
            .beginControlFlow(
                "object : %T<%T>",
                ClassName.bestGuess(PolicyHandler::class.qualifiedName!!),
                stateType  // Use specific type
            )
            .beginControlFlow(
                "override suspend fun getState(parameters: %T): %T",
                ClassName.bestGuess(PolicyParameters::class.qualifiedName!!),
                stateType  // Use specific type
            )
            .addStatement("return policyImpl.getState(parameters)")
            .endControlFlow()
            .beginControlFlow(
                "override suspend fun setState(newState: %T): %T<Unit>",
                stateType,  // Use specific type
                ClassName.bestGuess(ApiResult::class.qualifiedName!!)
            )
            .addStatement("return policyImpl.setState(newState)")
            .endControlFlow()
            .endControlFlow()
            .build()
    }

    private fun writeToFile(componentSpec: TypeSpec, policy: ProcessedPolicy) {
        try {
            val packageName = getGeneratedPackage()

            environment.codeGenerator.createNewFile(
                Dependencies(false),
                packageName,
                "${policy.className}Component"
            ).use { output ->
                output.writer().use { writer ->
                    FileSpec.builder(packageName, "${policy.className}Component")
                        .addType(componentSpec)
                        .build()
                        .writeTo(writer)
                }
            }
        } catch (_: FileAlreadyExistsException) {
            environment.logger.warn("Component file already exists for ${policy.className}. Skipping generation.")
        }
    }

    private fun getGeneratedPackage(): String =
        GeneratedPackages.getPolicyPackage(environment)
}