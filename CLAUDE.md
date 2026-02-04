# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

knox-core is the foundational library for Knox SDK modules. It provides DI-agnostic infrastructure that works with Hilt, Koin, or no DI framework.

## Build Commands

Run from the parent project root (`knoxmoduleshowcase/`):

- **Build knox-core**: `./gradlew :knox-core:build` or build individual submodules like `:knox-core:feature:build`
- **Run unit tests**: `./gradlew :knox-core:test` or `:knox-core:common:test`
- **Run single test class**: `./gradlew :knox-core:feature:test --tests "net.sfelabs.knox.core.feature.DefaultFeatureRegistryTest"`
- **Run single test method**: `./gradlew :knox-core:feature:test --tests "*.DefaultFeatureRegistryTest.testMethodName"`

## Module Structure

```
knox-core/
├── android/           - Context provider (service locator pattern for KSP compatibility)
├── common/            - DataStore, preferences, coroutines, utilities
├── feature/           - Policy framework, registry, grouping strategies, capabilities
├── feature-processor/ - KSP processor generating policy components
├── testing/           - Fakes, test rules, context setup
├── ui/                - Shared Compose components
└── usecase-executor/  - SuspendingUseCase base class, ApiResult
```

## Key Architectural Patterns

### Service Locator for Context

KSP runs before Hilt, so generated code cannot use constructor injection. Use cases implement `WithAndroidApplicationContext` to access context via static provider:

```kotlin
class MyUseCase : WithAndroidApplicationContext, SuspendingUseCase<Unit, Result>() {
    override suspend fun execute(params: Unit): ApiResult<Result> {
        val service = applicationContext.getSystemService(...)
    }
}
```

### DI-Agnostic Singletons

`DataStoreSource` and `PreferencesRepository` use hybrid singletons:

```kotlin
// Without DI
val prefs = PreferencesRepository.getInstance(context)

// With Hilt (knox-hilt calls setInstance())
@Inject lateinit var prefs: PreferencesRepository
```

### Policy Framework

Policies use `@PolicyDefinition` annotation. KSP generates:
- `*Component` - wraps the policy
- `*Key` - type-safe lookup key
- `GeneratedPolicyComponents` - DI-agnostic list of all components

Registry supports capability-based queries:
```kotlin
registry.getByCapability(PolicyCapability.MODIFIES_RADIO)
registry.getByCapabilities(setOf(MODIFIES_RADIO, REQUIRES_SIM), matchAll = true)
```

### Grouping Strategies

UI grouping is separate from policy definitions:
```kotlin
val strategy = CapabilityBasedGroupingStrategy()  // Default: by capability
val groups = strategy.resolveAllGroups(registry)
```

## Testing Patterns

### Context Setup (instrumentation tests)

```kotlin
@get:Rule
val contextRule = AndroidContextProviderRule()
```

### Fakes over Mocks

Use `FakePreferencesRepository` and `FakeDataStoreSource` for behavior-based testing:
```kotlin
private val repository = FakePreferencesRepository()

@Test
fun test() = runTest {
    repository.setValueSync("key", true)
    // test use case
    assertEquals(true, repository.getStoredValue<Boolean>("key"))
}
```

### Test Dispatcher

```kotlin
private val testDispatcher = UnconfinedTestDispatcher()
private val dispatcherProvider = TestDispatcherProvider(testDispatcher)
```

## Common Capabilities

| Category | Values |
|----------|--------|
| Modifies | `MODIFIES_RADIO`, `MODIFIES_WIFI`, `MODIFIES_BLUETOOTH`, `MODIFIES_DISPLAY`, `MODIFIES_AUDIO`, `MODIFIES_CHARGING`, `MODIFIES_NETWORK`, etc. |
| Requires | `REQUIRES_SIM`, `REQUIRES_HDM`, `REQUIRES_DUAL_SIM` |
| Impact | `SECURITY_SENSITIVE`, `AFFECTS_CONNECTIVITY`, `AFFECTS_BATTERY`, `REQUIRES_REBOOT` |

## Use Case Parameter Convention

Always name the parameter `params` in execute() methods:
```kotlin
override suspend fun execute(params: Boolean): ApiResult<Unit>  // Correct
override suspend fun execute(enabled: Boolean): ApiResult<Unit> // Causes warning
```

## Policy State Refresh Pattern

**IMPORTANT:** After setting a policy state, always use the refreshed state from the device rather than the state from `PolicyConfiguration.fromUiState()`.

### Why Refresh is Needed

`fromUiState()` only receives UI-derived inputs (enabled flag and configuration options). It **cannot preserve non-UI state fields** that exist in the PolicyState, such as:
- Device capability masks (e.g., `HdmState.supportedMask`)
- Cached API data
- Computed fields not represented in ConfigurationOptions

If you use the `fromUiState()` result directly for UI updates, these fields will have default values (typically 0 or null), causing incorrect UI rendering.

### Option 1: Use `setAndRefreshPolicyState()` (Recommended)

For most cases, use the convenience method that handles refresh automatically:

```kotlin
when (val result = registry.setAndRefreshPolicyState(key, newState)) {
    is ApiResult.Success -> {
        // result.data contains complete state including device-derived fields
        updateUi(result.data)
    }
    is ApiResult.Error -> handleError(result)
    ApiResult.NotSupported -> handleNotSupported()
}
```

### Option 2: Manual Refresh with `setPolicyState()`

For cases where you need more control or don't need the refreshed state:

```kotlin
when (val result = registry.setPolicyState(key, newState)) {
    is ApiResult.Success -> {
        // IMPORTANT: newState from fromUiState() is incomplete
        // Must refresh to get device-derived fields
        val refreshed = registry.getPolicyState(featureName)
        updateUi(refreshed)
    }
    is ApiResult.Error -> {
        // Revert to original state, show error
    }
}
```

### Incorrect Pattern

```kotlin
when (val result = registry.setPolicyState(key, newState)) {
    is ApiResult.Success -> {
        // WRONG: newState from fromUiState() is missing non-UI fields
        updateUi(newState)  // Will have supportedMask=0, etc.
    }
}
```

## Mocking

This project uses MockK, not Mockito.
