# gradle-errorprone-javacplugin-plugin

This plugin configures `JavaCompile` tasks to use the [Error Prone compiler] as a [javac plugin].

[Error Prone compiler]: http://errorprone.info/
[javac plugin]: https://docs.oracle.com/javase/9/docs/api/com/sun/source/util/Plugin.html

## Requirements

Error Prone requires at least a JDK 9 compiler to be used as a javac plugin.
This means either running Gradle with,
or [configuring `JavaCompile` tasks][ForkOptions.setJavaHome] to use such a JDK.

[ForkOptions.setJavaHome]: https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/compile/ForkOptions.html#setJavaHome-java.io.File-

Applying this plugin also requires using at least Gradle 4.6.

## Usage

```gradle
plugins {
    id("net.ltgt.errorprone-javacplugin") version "<plugin version>"
}
```

This plugin creates a configuration named `errorprone`,
and configures the `<sourceSet>AnnotationProcessor` configuration for each source set to extend it.
This allows configuring Error Prone dependencies from a single place.

Error Prone needs to be added as a dependency in this configuration:
```gradle
repositories {
    mavenCentral()
}
dependencies {
    errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
}
```
or it will default to using the `latest.release` version
(but will still require a repository to be configured to find the dependency).

It can then be configured on the `JavaCompile` tasks:
```gradle
tasks.withType(JavaCompile) {
    options.errorprone.disableWarningsInGeneratedCode = true
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
import net.ltgt.gradle.errorprone.javacplugin.*

tasks.withType<JavaCompile> {
    options.errorprone.disableWarningsInGeneratedCode = true
}
```

</details>

and can also be disabled altogether:
```gradle
compileTestJava {
    options.errorprone.enabled = false
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
val compileTestJava by tasks.getting(JavaCompile::class) {
    options.errorprone.isEnabled = false
}
```

</details>

Note that this plugin only configures tasks for source sets
(i.e. `compileJava` for the `main` source set, `compileTestJava` for the `test` source set,
and `compileIntegTestJava` for a custom `integTest` source set).
If you're creating custom `JavaCompile` tasks,
then you'll have to configure them manually:
```gradle
task compileCustom(type: JavaCompile) {
    source "src/custom/"
    include "**/*.java"
    classpath = configurations.custom
    sourceCompatibility = "8"
    targetCompatibility = "8"
    destinationDir = file("$buildDir/classes/custom")

    // Enable Error Prone
    ErrorProneJavacPlugin.apply(options)
    // Error Prone must be available in the annotation processor path
    options.annotationProcessorPath = configurations.errorprone
    // It can then be configured for the task
    options.errorprone.disableWarningsInGeneratedCode = true
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
val compileCustom by tasks.creating(JavaCompile::class) {
    source("src/custom/")
    include("**/*.java")
    classpath = configurations["custom"]
    sourceCompatibility = "8"
    targetCompatibility = "8"
    destinationDir = file("$buildDir/classes/custom")

    // Enable Error Prone
    ErrorProneJavacPlugin.apply(options)
    // Error Prone must be available in the annotation processor path
    options.annotationProcessorPath = configurations["errorprone"]
    // It can then be configured for the task
    options.errorprone.disableWarningsInGeneratedCode = true
}
```

</details>

This currently also applies to Android projects,
which do not use the standard `java` plugin and standard source sets:
```gradle
tasks.withType(JavaCompile) {
    ErrorProneJavacPlugin.apply(options)
    options.errorprone.disableWarningsInGeneratedCode = true
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
tasks.withType<JavaCompile> {
    ErrorProneJavacPlugin.apply(options)
    options.errorprone.disableWarningsInGeneratedCode = true
}
```

</details>

## Custom Error Prone checks

**This currently does not work; see https://github.com/google/error-prone/issues/974**

<details>
<summary>See details anyway</summary>

[Custom Error Prone checks][custom checks] can be added to the `errorprone` configuration too:
```gradle
dependencies {
    errorprone("com.uber.nullaway:nullaway:$nullawayVersion")
}
```
or alternatively to the `<sourceSet>AnnotationProcessor` configuration,
if they only need to be enabled for a given source set:
```gradle
dependencies {
    annotationProcessor("com.google.guava:guava-beta-checker:$betaCheckerVersion")
}
```
and can then be configured on the tasks; for example:
```gradle
tasks.withType(JavaCompile) {
    options.errorprone {
        option("NullAway:AnnotatedPackages", "net.ltgt")
    }
}
compileJava {
    // Check defaults to WARNING, bump it up to ERROR for the main sources
    options.errorprone.check("NullAway", CheckSeverity.ERROR)
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
tasks.withType<JavaCompile> {
    options.errorprone {
        option("NullAway:AnnotatedPackages", "net.ltgt")
    }
}
val compileJava by tasks.getting(JavaCompile::class) {
    // Check defaults to WARNING, bump it up to ERROR for the main sources
    options.errorprone.check("NullAway", CheckSeverity.ERROR)
}
```

</details>

[custom checks]: http://errorprone.info/docs/plugins

</details>

## Configuration

As noted above, this plugin adds an `errorprone` extension to the `JavaCompile.options`.
It can be configured either as a property (`options.errorprone.xxx = …`)
or script block (`options.errorprone { … }`).

In a `*.gradle.kts` script, the Kotlin extensions need to be imported:
```kotlin
import net.ltgt.gradle.errorprone.javacplugin.*
```

### Properties

| Property | Description
| :------- | :----------
| `enabled`                        | (`isEnabled` with Kotlin DSL) Allows disabling Error Prone altogether for the task. Defaults to `true`.
| `disableAllChecks`               | Disable all Error Prone checks. This will be the first argument, so checks can then be re-enabled on a case-by-case basis. Defaults to `false`.
| `allErrorsAsWarnings`            | Defaults to `false`.
| `allDisabledChecksAsWarnings`    | Enables all Error Prone checks, checks that are disabled by default are enabled as warnings. Defaults to `false`.
| `disableWarningsInGeneratedCode` | Disables warnings in classes annotated with `javax.annotation.processing.Generated` or `@javax.annotation.Generated`. Defaults to `false`.
| `ignoreUnknownCheckNames`        | Defaults to `false`.
| `compilingTestOnlyCode`          | (`isCompilingTestOnlyCode` with Kotlin DSL) Defaults to `false`. (defaults to `true` for the `compileTestJava` task)
| `excludedPaths`                  | A regular expression pattern (as a string) of file paths to exclude from Error Prone checking. Defaults to `null`.
| `checks`                         | A map of check name to `CheckSeverity`, to configure which checks are enabled or disabled, and their severity. Defaults to an empty map.
| `checkOptions`                   | A map of check options to their value. Use an explicit `"true"` value for a boolean option. Defaults to an empty map.
| `errorproneArgs`                 | Additional arguments passed to Error Prone. Defaults to an empty list.
| `errorproneArgumentProviders`    | A list of [`CommandLineArgumentProvider`] for additional arguments passed to Error Prone. Defaults to an empty list.

[`CommandLineArgumentProvider`]: https://docs.gradle.org/current/javadoc/org/gradle/process/CommandLineArgumentProvider.html

### Methods

| Method | Description
| :----- | :----------
| `check(checkNames...)`            | Adds checks with their default severity. Useful in combination with `disableAllChecks` to selectively re-enable checks.
| `check(checkName to severity...)` | (Kotlin DSL only) Adds pairs of check name to severity. Severity can be set to `CheckSeverity.OFF` to disable a check.
| `check(checkName, severity)`      | Adds a check with a given severity. Severity can be set to `CheckSeverity.OFF` to disable the check.
| `option(optionName)`              | Enables a boolean check option. Equivalent to `option(checkName, "true")`.
| `option(optionName, value)`       | Adds a check option with a given value.

A check severity can take values: `DEFAULT`, `OFF`, `WARN`, or `ERROR`.
