package net.sfelabs.knox.core.feature.processor.generator

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import net.sfelabs.knox.core.feature.api.PolicyComponent
import net.sfelabs.knox.core.feature.api.PolicyState
import net.sfelabs.knox.core.feature.processor.model.ProcessedPolicy
import net.sfelabs.knox.core.feature.processor.utils.GeneratedPackages

/**
 * Generates a DI-agnostic registry class containing all policy components.
 *
 * This generator creates a `GeneratedPolicyComponents` object that provides
 * a `getAll()` function returning all policy components. This allows DI frameworks
 * (Hilt, Koin, etc.) to consume the components without the policy module having
 * any DI framework dependencies.
 *
 * ## Generated Code Structure
 *
 * ```kotlin
 * object GeneratedPolicyComponents {
 *     fun getAll(): List<PolicyComponent<out PolicyState>> =
 *         group0() + group1() // Split for method size limits
 *
 *     private fun group0(): List<PolicyComponent<out PolicyState>> = listOf(
 *         Policy1Component(),
 *         Policy2Component(),
 *         // ...
 *     )
 * }
 * ```
 *
 * ## Method Size Splitting
 *
 * To avoid the JVM 64KB method bytecode limit, policies are split into groups
 * of [GROUP_SIZE] (~150). Each group is a private function, and `getAll()`
 * concatenates them.
 */
class RegistryGenerator(
    private val environment: SymbolProcessorEnvironment
) {
    companion object {
        private const val GROUP_SIZE = 150
        private const val CLASS_NAME = "GeneratedPolicyComponents"
    }

    fun generate(policies: List<ProcessedPolicy>) {
        if (policies.isEmpty()) return

        val packageName = GeneratedPackages.getPolicyPackage(environment)
        val groups = policies.chunked(GROUP_SIZE)

        val registrySpec = TypeSpec.objectBuilder(CLASS_NAME)
            .addFunction(buildGetAllFunction(groups))
            .addFunctions(buildGroupFunctions(policies, groups))
            .build()

        writeToFile(registrySpec, packageName)
    }

    private fun buildGetAllFunction(groups: List<List<ProcessedPolicy>>): FunSpec {
        val returnType = List::class.asClassName().parameterizedBy(
            PolicyComponent::class.asClassName().parameterizedBy(
                WildcardTypeName.producerOf(PolicyState::class.asClassName())
            )
        )

        val groupCalls = if (groups.size == 1) {
            "group0()"
        } else {
            groups.indices.joinToString(" + ") { "group$it()" }
        }

        return FunSpec.builder("getAll")
            .returns(returnType)
            .addStatement("return $groupCalls")
            .build()
    }

    private fun buildGroupFunctions(
        policies: List<ProcessedPolicy>,
        groups: List<List<ProcessedPolicy>>
    ): List<FunSpec> {
        val packageName = GeneratedPackages.getPolicyPackage(environment)
        val returnType = List::class.asClassName().parameterizedBy(
            PolicyComponent::class.asClassName().parameterizedBy(
                WildcardTypeName.producerOf(PolicyState::class.asClassName())
            )
        )

        return groups.mapIndexed { index, group ->
            val componentInstantiations = group.joinToString(",\n        ") { policy ->
                "${policy.className}Component()"
            }

            FunSpec.builder("group$index")
                .addModifiers(KModifier.PRIVATE)
                .returns(returnType)
                .addStatement(
                    """return listOf(
        $componentInstantiations
    )"""
                )
                .build()
        }
    }

    private fun writeToFile(registrySpec: TypeSpec, packageName: String) {
        try {
            environment.codeGenerator.createNewFile(
                Dependencies(false),
                packageName,
                CLASS_NAME
            ).use { output ->
                output.writer().use { writer ->
                    FileSpec.builder(packageName, CLASS_NAME)
                        .addType(registrySpec)
                        .build()
                        .writeTo(writer)
                }
            }
        } catch (_: FileAlreadyExistsException) {
            environment.logger.warn("$CLASS_NAME file already exists. Skipping generation.")
        }
    }
}
