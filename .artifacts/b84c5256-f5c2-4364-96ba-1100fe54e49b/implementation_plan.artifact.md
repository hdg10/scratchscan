# Fix KSP Plugin Not Found and Version Mismatch

The build error is caused by a combination of incorrect KSP versioning, a mismatch between Kotlin versions in the project, and a restrictive repository configuration in `settings.gradle.kts`.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/hchas/AndroidStudioProjects/scratchscan/gradle/libs.versions.toml)
- Update `kotlin` version to `2.4.10` to match the latest stable release and the serialization plugin usage.
- Update `ksp` version to `2.3.11`, which is the correct independent version for KSP2 in the current environment (August 2026).
- Add the Kotlin Serialization plugin definition to the `[plugins]` section.

#### [MODIFY] [build.gradle.kts (app)](file:///C:/Users/hchas/AndroidStudioProjects/scratchscan/app/build.gradle.kts)
- Update the `plugins` block to use the `libs` alias for the Kotlin Serialization plugin instead of a hardcoded version.

#### [MODIFY] [settings.gradle.kts](file:///C:/Users/hchas/AndroidStudioProjects/scratchscan/settings.gradle.kts)
- Refine the `google` repository `includeGroupByRegex` to prevent it from capturing `com.google.devtools.ksp`, allowing it to be resolved from `mavenCentral()` or `gradlePluginPortal()`.

## Verification Plan

### Automated Tests
- Run `./gradlew build` to verify that the project compiles successfully and all plugins are resolved.
- Run `./gradlew kspDebugKotlin` to ensure KSP is correctly generating code (e.g., for Room).
