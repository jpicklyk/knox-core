# knox-core

Core infrastructure library for Knox SDK modules. This module provides shared utilities, base classes, and abstractions used by knox-enterprise and other Knox modules.

## Overview

knox-core is a multi-module library that provides the foundational components for building Knox-based Android applications. It is designed to be DI-agnostic and can be used with any dependency injection framework.

## Module Structure

```
knox-core/
├── android/          - Android-specific utilities and context providers
├── common/           - Shared models, utilities, and domain logic
├── feature/          - Policy framework and registry system
├── feature-processor/- KSP annotation processor for policy generation
├── testing/          - Test utilities and helpers
├── ui/               - Shared UI components
└── usecase-executor/ - Use case execution framework
```

## Submodules

### android

Android-specific utilities for context management:

- `AndroidApplicationContextProvider` - Interface for providing application context
- `WithAndroidApplicationContext` - Mixin for classes requiring Android context

### common

Shared utilities and domain logic:

- **Coroutines** - `DispatcherProvider` for coroutine dispatcher abstraction
- **Data** - `DataStoreSource` for preferences storage
- **Domain** - Utility functions for Knox HDM, netmask calculations, package management
- **Presentation** - `ResourceProvider` for string resource access
- **Result** - Generic `Result` type for operation outcomes
- **UiText** - Abstraction for UI text (string resources or dynamic text)

### feature

Policy framework for managing Knox policies:

- **Policy API** - Interfaces for defining policies (`PolicyComponent`, `PolicyContract`, `PolicyDescriptor`)
- **Policy State** - State management (`PolicyState`, `BooleanPolicyState`, `ConfigurableStatePolicy`)
- **Policy Registry** - Registration and lookup (`DefaultPolicyRegistry`, `CachedPolicyRegistry`)
- **Annotations** - `@PolicyDefinition` for code generation

### feature-processor

KSP annotation processor that generates policy boilerplate from `@PolicyDefinition` annotations.

### testing

Test utilities and helpers:

- Test annotations (`@DebugUsbRequired`, `@TacticalSdkSuppress`)
- Test rules and utilities for Knox testing

### ui

Shared Compose UI components for Knox features.

### usecase-executor

Framework for executing use cases:

- `BaseUseCase` - Base class for synchronous use cases
- `SuspendingUseCase` - Base class for coroutine-based use cases
- `UseCaseBuilder` - DSL for building and executing use cases
- `ApiResult` - Result type for API operations with error handling

## Architecture

knox-core follows a DI-agnostic architecture:

```
┌─────────────────────────────────────────────────────────┐
│  Your App                                               │
├─────────────────────────────────────────────────────────┤
│  knox-hilt (optional - for Hilt users)                  │
├─────────────────────────────────────────────────────────┤
│  Knox Feature Modules                                   │
│  ┌─────────────────┐                                    │
│  │ knox-enterprise │                                    │
│  └─────────────────┘                                    │
├─────────────────────────────────────────────────────────┤
│  knox-core (this module)                                │
│  - Shared infrastructure and utilities                  │
│  - Policy framework                                     │
│  - Use case execution                                   │
└─────────────────────────────────────────────────────────┘
```

## Usage

### Adding Dependencies

```kotlin
// In your module's build.gradle.kts
dependencies {
    // Core utilities
    implementation(project(":knox-core:common"))

    // Policy framework
    implementation(project(":knox-core:feature"))

    // Use case framework
    implementation(project(":knox-core:usecase-executor"))

    // Android context utilities
    implementation(project(":knox-core:android"))

    // UI components
    implementation(project(":knox-core:ui"))

    // Testing utilities
    testImplementation(project(":knox-core:testing"))
}
```

### Using the Use Case Framework

```kotlin
// Define a use case
class GetBrightnessUseCase : SuspendingUseCase<Unit, Int>() {
    override suspend fun execute(params: Unit): ApiResult<Int> {
        return try {
            val brightness = // ... get brightness value
            ApiResult.Success(brightness)
        } catch (e: Exception) {
            ApiResult.Error(DefaultApiError(e.message ?: "Unknown error"))
        }
    }
}

// Execute the use case
val result = GetBrightnessUseCase().invoke(Unit)
when (result) {
    is ApiResult.Success -> println("Brightness: ${result.data}")
    is ApiResult.Error -> println("Error: ${result.error.message}")
}
```

### Using the Policy Framework

```kotlin
// Define a policy
@PolicyDefinition(
    key = "screen_brightness",
    category = PolicyCategory.DISPLAY
)
class ScreenBrightnessPolicy : ConfigurableStatePolicy<Int> {
    override suspend fun getState(): PolicyState<Int> { ... }
    override suspend fun setState(value: Int): Result<Unit> { ... }
}

// Register and access policies
val registry: PolicyRegistry = DefaultPolicyRegistry()
registry.register(ScreenBrightnessPolicy())

val policy = registry.getPolicy<ScreenBrightnessPolicy>("screen_brightness")
```

### Using Resource Provider

```kotlin
// Create a resource provider
val resourceProvider = ResourceProviderFactory.create(context)

// Access string resources
val text = resourceProvider.getString(R.string.my_string)
val formattedText = resourceProvider.getString(R.string.my_format, arg1, arg2)
```

## Testing

knox-core provides testing utilities in the `testing` submodule:

```kotlin
// Use test annotations
@DebugUsbRequired
class MyUsbTests {
    // Tests that require USB debugging
}

@TacticalSdkSuppress(minReleaseVersion = 100)
class MyTacticalTests {
    // Tests for specific SDK versions
}
```

## Requirements

- Android API 21+
- Kotlin 1.9+
- Coroutines 1.7+

## Design Principles

1. **DI-Agnostic** - No dependency on specific DI frameworks
2. **Modular** - Use only the submodules you need
3. **Testable** - All components designed for easy testing
4. **Type-Safe** - Strong typing with sealed classes and generics
5. **Coroutine-First** - Built for Kotlin coroutines
