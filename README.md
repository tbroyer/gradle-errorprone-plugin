# gradle-errorprone-plugin

This plugin configures `JavaCompile` tasks to use [Error Prone].

[Error Prone]: https://errorprone.info/

## Requirements

> [!IMPORTANT]
> This plugin requires using at least Gradle 6.8 and JDK 11 (for compilation; it's OK to use JDK 8 to run Gradle as long as compilations use at least JDK 11 through [Gradle Java Toolchains][gradle-toolchains]).

The exact minimum required version of the JDK depends on the version of Error Prone being used (independently of the version of this plugin);
there's no forward compatibility guarantee though so older versions of Error Prone aren't necessarily compatible with newer versions of the JDK.

| Error Prone version  | Minimum JDK version |
| :------------------: | :-----------------: |
| Up to 2.31           | 11                  |
| From 2.32 up to 2.42 | 17                  |
| Starting from 2.43   | 21                  |

You can still compile down to a lower Java version bytecode though, by using javac's support for `--release` (which you can configure with Gradle using [`options.release`][CompileOptions.release]), or with `-source` / `-target` / `-bootclasspath` (configured with Gradle using [`sourceCompatibility`][JavaCompile.sourceCompatibility] / [`targetCompatibility`][JavaCompile.targetCompatibility] / [`options.bootstrapClasspath`][CompileOptions.bootstrapClasspath]).

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.release = 8
}
```
<details>
<summary>with Groovy DSL</summary>

```gradle
tasks.withType(JavaCompile).configureEach {
    options.release = 8
}
```

</details>

There's no specific support for the [Android Gradle Plugin](https://developer.android.com/build).
Read on to better understand what you need to do to use both plugins together.
Specifically, note that _source sets_ below are only about [standard Gradle source sets for JVM projects](https://docs.gradle.org/current/userguide/building_java_projects.html#sec:java_source_sets), not [Android source sets](https://developer.android.com/build#sourcesets), so anything done by the plugin based on source sets won't be done at all for Android projects.

[gradle-toolchains]: https://docs.gradle.org/current/userguide/toolchains.html
[CompileOptions.release]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:release
[JavaCompile.sourceCompatibility]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.compile.JavaCompile.html#org.gradle.api.tasks.compile.JavaCompile:sourceCompatibility
[JavaCompile.targetCompatibility]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.compile.JavaCompile.html#org.gradle.api.tasks.compile.JavaCompile:targetCompatibility
[CompileOptions.bootstrapClasspath]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:bootstrapClasspath

## Usage

```kotlin
plugins {
    id("net.ltgt.errorprone") version "<plugin version>"
}
```

This plugin creates a configuration named `errorprone`,
and configures the `<sourceSet>AnnotationProcessor` configuration for each source set to extend it.
This allows configuring Error Prone dependencies from a single place.

Error Prone needs to be added as a dependency in this configuration:
```kotlin
repositories {
    mavenCentral()
}
dependencies {
    errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")
}
```

> [!CAUTION]
> Using a dynamic or changing version for Error Prone,
> such as `latest.release` or `2.+`,
> means that your build could fail at any time,
> if a new version of Error Prone adds or enables new checks that your code would trigger.

Error Prone can then be [configured](#configuration) on the `JavaCompile` tasks:
```kotlin
import net.ltgt.gradle.errorprone.errorprone

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.disableWarningsInGeneratedCode = true
}
```
<details>
<summary>with Groovy DSL</summary>

```gradle
tasks.withType(JavaCompile).configureEach {
    options.errorprone.disableWarningsInGeneratedCode = true
}
```

</details>

and can also be disabled altogether:
```kotlin
tasks {
    compileTestJava {
        options.errorprone.isEnabled.set(false)
    }
}
```
<details>
<summary>with Groovy DSL</summary>

```gradle
tasks {
    compileTestJava {
        options.errorprone.enabled = false
    }
}
```

</details>

Note that this plugin only enables Error Prone on tasks for source sets
(i.e. `compileJava` for the `main` source set, `compileTestJava` for the `test` source set,
and `compileIntegTestJava` for a custom `integTest` source set).

<details>
<summary>If you're creating custom compile tasks,
then you'll have to configure them manually to enable Error Prone</summary>

```kotlin
val annotationProcessorCustom = configurations.resolvable("annotationProcessorCustom") {
    extendsFrom(configurations.errorprone.get())
}
tasks.register<JavaCompile>("compileCustom") {
    source("src/custom/")
    include("**/*.java")
    classpath = configurations["custom"]
    sourceCompatibility = "11"
    targetCompatibility = "11"
    destinationDirectory = file("$buildDir/classes/custom")

    // Error Prone must be available in the annotation processor path
    options.annotationProcessorPath = annotationProcessorCustom.get()
    // Enable Error Prone
    options.errorprone.isEnabled = true
    // It can then be configured for the task
    options.errorprone.disableWarningsInGeneratedCode = true
}
```
<details>
<summary>with Groovy DSL</summary>

```gradle
def annotationProcessorCustom = configurations.resolvable("annotationProcessorCustom") {
    extendsFrom(configurations.errorprone)
}
tasks.register("compileCustom", JavaCompile) {
    source "src/custom/"
    include "**/*.java"
    classpath = configurations.custom
    sourceCompatibility = "11"
    targetCompatibility = "11"
    destinationDirectory = file("$buildDir/classes/custom")

    // Error Prone must be available in the annotation processor path
    options.annotationProcessorPath = annotationProcessorCustom
    // Enable Error Prone
    options.errorprone.enabled = true
    // It can then be configured for the task
    options.errorprone.disableWarningsInGeneratedCode = true
}
```

</details>
</details>

## JDK 16+ support

Starting with JDK 16, due to [JEP 396: Strongly Encapsulate JDK Internals by Default][jep396],
`--add-opens` and `--add-exports` arguments need to be passed to the compiler's JVM.

The plugin will automatically [use a forking compiler][CompileOptions.fork]
and pass the necessary [JVM arguments][BaseForkOptions.getJvmArgs]
whenever it detects such a JDK is being used for the compilation task and ErrorProne is enabled
(unless the Gradle daemon's JVM already was given the appropriate options [through `org.gradle.jvmargs`][org.gradle.jvmargs]).

That detection will only take into account the [toolchain][gradle-toolchains] used by the `JavaCompile` task,
or the JDK used to run Gradle in case no toolchain is being used.
The plugin will ignore any task that [forks][CompileOptions.fork] and defines either [a `javaHome`][ForkOptions.setJavaHome] or [an `executable`][ForkOptions.setExecutable],
and thus won't configure the JVM arguments in this case.

Note that the plugin also configures the JVM arguments for any JDK below 16 to silence related warnings,
but they will then only be used if the task is explicitly configured for forking
(or if the configured toolchain is incompatible with the JDK used to run Gradle, which will then implicitly fork a compiler daemon).

[jep396]: https://openjdk.java.net/jeps/396
[CompileOptions.fork]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:fork
[BaseForkOptions.getJvmArgs]: https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/compile/BaseForkOptions.html#getJvmArgs--
[org.gradle.jvmargs]: https://docs.gradle.org/current/userguide/build_environment.html#sec:configuring_jvm_memory
[ForkOptions.setJavaHome]: https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/compile/ForkOptions.html#setJavaHome-java.io.File-
[ForkOptions.setExecutable]: https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/compile/ForkOptions.html#setExecutable-java.lang.String-

## Android Gradle Plugin support

As noted above, this plugin won't have much effect when used in conjunction with the AGP rather than, say, Gradle's built-in Java plugins.

It will then:
* create the `errorprone` configuration, but won't wire it to any other configuration, and by extension to any compilation task
* enhance `JavaCompile` tasks with the `errorprone` extension, but keep ErrorProne disabled by default (it would fail otherwise, as ErrorProne won't be on the processor path)

You'll thus have to somehow:
* put ErrorProne on the processor path of the `JavaCompile` tasks
* enable ErrorProne on the `JavaCompile` tasks
* configure `isCompilingTestOnlyCode` for compilation tasks for test variants (this changes the behavior of some checks)

This could (and should) be done by a plugin, so if you have deep knowledge of the [AGP APIs](https://developer.android.com/build/extend-agp) and how to idiomatically integrate Error Prone within Android builds, please make such a plugin and I'll link to it here for others to use.

## Custom Error Prone checks

[Custom Error Prone checks][custom checks] can be added to the `errorprone` configuration too:
```kotlin
dependencies {
    errorprone("com.uber.nullaway:nullaway:$nullawayVersion")
}
```
or alternatively to the `<sourceSet>AnnotationProcessor` configuration,
if they only need to be enabled for a given source set:
```kotlin
dependencies {
    annotationProcessor("com.google.guava:guava-beta-checker:$betaCheckerVersion")
}
```
and can then be configured on the tasks; for example:
```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        option("NullAway:AnnotatedPackages", "net.ltgt")
    }
}
tasks.compileJava {
    // The check defaults to a warning, bump it up to an error for the main sources
    options.errorprone.error("NullAway")
}
```
<details>
<summary>with Groovy DSL</summary>

```gradle
tasks.withType(JavaCompile).configureEach {
    options.errorprone {
        option("NullAway:AnnotatedPackages", "net.ltgt")
    }
}
tasks.compileJava {
    // The check defaults to a warning, bump it up to an error for the main sources
    options.errorprone.error("NullAway")
}
```

</details>

> [!NOTE]
> These examples use NullAway.
> Note that I made [a companion plugin][gradle-nullaway-plugin] specifically to surface NullAway options as Gradle DSL properties.

[custom checks]: https://errorprone.info/docs/plugins
[gradle-nullaway-plugin]: https://github.com/tbroyer/gradle-nullaway-plugin

## Configuration

As noted above, this plugin adds an `errorprone` extension to the `JavaCompile.options`.
It can be configured either as a property (`options.errorprone.xxx`)
or script block (`options.errorprone { … }`).

In a `*.gradle.kts` script, the Kotlin extensions need to be imported:
```kotlin
import net.ltgt.gradle.errorprone.errorprone
```

### Properties

_Please note that all properties are [lazy](https://docs.gradle.org/current/userguide/lazy_configuration.html#lazy_properties)._

| Property | Description
| :------- | :----------
| `isEnabled`                      | (`enabled` with Groovy DSL) Allows disabling Error Prone altogether for the task. Error Prone will still be in the annotation processor path, but `-Xplugin:ErrorProne` won't be passed as a compiler argument. Defaults to `true` for source set tasks, `false` otherwise.
| `disableAllChecks`               | Disable all Error Prone checks; maps to `-XepDisableAllChecks`. This will be the first argument, so checks can then be re-enabled on a case-by-case basis. Defaults to `false`.
| `disableAllWarnings`             | Maps to `-XepDisableAllWarnings` (since ErrorProne 2.4.0). Defaults to `false`.
| `allErrorsAsWarnings`            | Maps to `-XepAllErrorsAsWarnings`. Defaults to `false`.
| `allSuggestionsAsWarnings`       | (since ErrorProne 2.20.0) Maps to `-XepAllSuggestionsAsWarnings`. Defaults to `false`.
| `allDisabledChecksAsWarnings`    | Enables all Error Prone checks, checks that are disabled by default are enabled as warnings; maps to `-XepDisabledChecksAsWarnings`. Defaults to `false`.
| `disableWarningsInGeneratedCode` | Disables warnings in classes annotated with `javax.annotation.processing.Generated` or `@javax.annotation.Generated`; maps to `-XepDisableWarningsInGeneratedCode`. Defaults to `false`.
| `ignoreUnknownCheckNames`        | Maps to `-XepIgnoreUnknownCheckNames`. Defaults to `false`.
| `ignoreSuppressionAnnotations`   | Maps to `-XepIgnoreSuppressionAnnotations` (since Error Prone 2.3.3). Defaults to `false`.
| `isCompilingTestOnlyCode`        | (`compilingTestOnlyCode` with Groovy DSL) Maps to `-XepCompilingTestOnlyCode`. Defaults to `false`. (defaults to `true` for a source set inferred as a test source set)
| `excludedPaths`                  | A regular expression pattern (as a string) of file paths to exclude from Error Prone checking; maps to `-XepExcludedPaths`. Defaults to `null`.
| `checks`                         | A map of check name to `CheckSeverity`, to configure which checks are enabled or disabled, and their severity; maps each entry to `-Xep:<key>:<value>`, or `-Xep:<key>` if the value is `CheckSeverity.DEFAULT`. Defaults to an empty map.
| `checkOptions`                   | A map of check options to their value; maps each entry to `-XepOpt:<key>=<value>`. Use an explicit `"true"` value for a boolean option. Defaults to an empty map.
| `errorproneArgs`                 | Additional arguments passed to Error Prone. Defaults to an empty list.
| `errorproneArgumentProviders`    | A list of [`CommandLineArgumentProvider`] for additional arguments passed to Error Prone. Defaults to an empty list.

[`CommandLineArgumentProvider`]: https://docs.gradle.org/current/javadoc/org/gradle/process/CommandLineArgumentProvider.html

### Methods

| Method | Description
| :----- | :----------
| `enable(checkNames...)`           | Adds checks with their default severity. Useful in combination with `disableAllChecks` to selectively re-enable checks. Equivalent to `check(checkName, CheckSeverity.DEFAULT)` for each check name.
| `disable(checkNames...)`          | Disable checks. Equivalent to `check(checkName, CheckSeverity.OFF)` for each check name.
| `warn(checkNames...)`             | Adds checks with warning severity. Equivalent to `check(checkName, CheckSeverity.WARNING)` for each check name.
| `error(checkNames...)`            | Adds checks with error severity. Equivalent to `check(checkName, CheckSeverity.ERROR)` for each check name.
| `check(checkName to severity...)` | (Kotlin DSL only) Adds pairs of check name to severity. Equivalent to `checks.put(first, second)` for each pair.
| `check(checkName, severity)`      | Adds a check with a given severity. The severity can be passed as a provider for lazy configuration. Equivalent to `checks.put(checkName, severity)`.
| `option(optionName)`              | Enables a boolean check option. Equivalent to `option(checkName, true)`.
| `option(optionName, value)`       | Adds a check option with a given value. Value can be a boolean or a string, or a provider of string. Equivalent to `checkOptions.put(name, value)`.

A check severity can take values: `DEFAULT`, `OFF`, `WARN`, or `ERROR`.
Note that the `net.ltgt.gradle.errorprone.CheckSeverity` needs to be `import`ed into your build scripts (see examples above).
