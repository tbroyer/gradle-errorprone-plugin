# gradle-errorprone-plugin

This plugin configures `JavaCompile` tasks to use the [Error Prone compiler].

[Error Prone compiler]: https://errorprone.info/

## Requirements

This plugin requires using at least Gradle 5.2.

While JDK 8 is supported, it is recommended to use at least a JDK 9 compiler.
See [note below](#jdk-8-support) about JDK 8 support.

## Usage

```gradle
plugins {
    id("net.ltgt.errorprone") version "<plugin version>"
}
```

_Note: snippets in this guide use features from the latest Gradle version, so beware if copy/pasting._

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

**CAUTION:** Using a dynamic or changing version for Error Prone,
such as `latest.release` or `2.+`,
means that your build could fail at any time,
if a new version of Error Prone adds or enables new checks that your code would trigger.

Error Prone can then be [configured](#configuration) on the `JavaCompile` tasks:
```gradle
tasks.withType(JavaCompile).configureEach {
    options.errorprone.disableWarningsInGeneratedCode = true
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
import net.ltgt.gradle.errorprone.errorprone

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.disableWarningsInGeneratedCode.set(true)
}
```

</details>

and can also be disabled altogether:
```gradle
tasks.named("compileTestJava").configure {
    options.errorprone.enabled = false
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone.isEnabled.set(false)
}
```

</details>

Note that this plugin only enables Error Prone on tasks for source sets
(i.e. `compileJava` for the `main` source set, `compileTestJava` for the `test` source set,
and `compileIntegTestJava` for a custom `integTest` source set).
If you're creating custom `JavaCompile` tasks,
then you'll have to configure them manually to enable Error Prone:
```gradle
tasks.register("compileCustom", JavaCompile) {
    source "src/custom/"
    include "**/*.java"
    classpath = configurations.custom
    sourceCompatibility = "8"
    targetCompatibility = "8"
    destinationDir = file("$buildDir/classes/custom")

    // Error Prone must be available in the annotation processor path
    options.annotationProcessorPath = configurations.errorprone
    // Enable Error Prone
    options.errorprone.enabled = true
    // It can then be configured for the task
    options.errorprone.disableWarningsInGeneratedCode = true
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
tasks.register<JavaCompile>("compileCustom") {
    source("src/custom/")
    include("**/*.java")
    classpath = configurations["custom"]
    sourceCompatibility = "8"
    targetCompatibility = "8"
    destinationDir = file("$buildDir/classes/custom")

    // Error Prone must be available in the annotation processor path
    options.annotationProcessorPath = configurations["errorprone"]
    // Enable Error Prone
    options.errorprone.isEnabled.set(true)
    // It can then be configured for the task
    options.errorprone.disableWarningsInGeneratedCode.set(true)
}
```

</details>

## JDK 8 support

Error Prone requires at least a JDK 9 compiler.
When using a JDK 8 compiler, you can configure a dependency on the Error Prone javac in the `errorproneJavac` configuration:
```gradle
dependencies {
    errorproneJavac("com.google.errorprone:javac:9+181-r4173-1")
}
```
and the plugin will configure the `JavaCompile` tasks to [use a forking compiler][CompileOptions.fork]
and will override the compiler by prepending the dependencies to the bootstrap classpath
(using a `-Xbootclasspath/p:` [JVM argument][BaseForkOptions.getJvmArgs]).

Starting with Gradle 6.7, you can [configure `JavaCompile` tasks][gradle-toolchains] to use a specific JDK compiler,
independently of the JDK used to run Gradle itself.
The plugin will use the toolchain version, if any is specified, to configure the task.
This allows you to enforce compilation with JDK 8 (properly configured for Error Prone!) while running Gradle with e.g. JDK 11,
or the reverse: enforce compilation with JDK 11 while running Gradle with JDK 8.

Alternatively, or for previous versions of Gradle, you can [configure `JavaCompile` tasks][ForkOptions.setJavaHome] to use such a JDK while still using JDK 8 for running Gradle:
```gradle
tasks.withType(JavaCompile).configureEach {
    options.fork(javaHome: project.getProperty("jdk11home"))
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.fork = true
    options.forkOptions.javaHome = project.getProperty("jdk11home")
}
```

</details>

The plugin will try to detect those cases and won't configure the bootstrap classpath in this case,
but to play safe it will actually ignore any task that forks and defines either a `javaHome` or an `executable`
(this also means that it won't configure the bootstrap classpath if you're e.g. running Gradle with a more recent JDK and forking the compilation tasks to use JDK 8).

If you need it, you can configure the bootstrap classpath manually for those tasks that the plugin would have skipped:
```gradle
someTask.configure {
    // …

    inputs.files(configurations.errorproneJavac).withNormalizer(ClasspathNormalizer)
    doFirst {
        options.forkOptions.jvmArgs += "-Xbootclasspath/p:${configurations.errorproneJavac.asPath}"
    }
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
someTask.configure {
    // …

    inputs.files(configurations.errorproneJavac).withNormalizer(ClasspathNormalizer::class)
    doFirst {
        options.forkOptions.jvmArgs.add("-Xbootclasspath/p:${configurations["errorproneJavac"].asPath}")
    }
}
```

</details>

(if you're using `forkOptions.executable`, then use `-J-Xbootclasspath/p:` instead.)

[gradle-toolchains]: https://docs.gradle.org/6.7/userguide/toolchains.html
[CompileOptions.fork]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.compile.CompileOptions.html#org.gradle.api.tasks.compile.CompileOptions:fork
[BaseForkOptions.getJvmArgs]: https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/compile/BaseForkOptions.html#getJvmArgs--
[ForkOptions.setJavaHome]: https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/compile/ForkOptions.html#setJavaHome-java.io.File-

## JDK 16+ support

Starting with JDK 16, due to [JEP 396: Strongly Encapsulate JDK Internals by Default][jep396],
`--add-opens` and `--add-exports` arguments need to be passed to the compiler's JVM.

The plugin will automatically [use a forking compiler][CompileOptions.fork]
and pass the necessary [JVM arguments][BaseForkOptions.getJvmArgs]
whenever it detects such a JDK is being used and ErrorProne is enabled.

That detection will only take into account the [toolchain][gradle-toolchains] used by the `JavaCompile` task,
or the JDK used to run Gradle in case no toolchain is being used.
This should also include toolchains set using `forkOptions.executable` or `forkOptions.javaHome`,
but only in Gradle 6.7+ where Gradle should expose those as ad-hoc toolchains,
and this hasn't been tested.

[jep396]: https://openjdk.java.net/jeps/396

## Migration from [versions 0.0._x_]

<details>
<summary>Details</summary>

If you relied on the default Error Prone dependency
(which you shouldn't have, see warning above about changing versions),
you'll have to configure it explicitly (see [above](#usage)).
If you need to support building with JDK 8,
you'll need to configure the Error Prone javac dependency (see [above](#jdk-8-support)).

Contrary to [versions 0.0._x_],
later versions use a DSL to configure Error Prone,
and passing Error Prone-specific arguments to `options.compilerArgs` won't work.
As an easy migration step,
you can pass those arguments to `options.errorprone.errorproneArgs` instead:
```diff
  tasks.withType(JavaCompile).configureEach {
-     options.compilerArgs << "-Xlint:all" << "-Werror" << "-XepDisableWarningsInGeneratedCode"
-     options.compilerArgs << "-Xep:NullAway:ERROR" << "-XepOpt:NullAway:AnnotatedPackages=net.ltgt"
+     options.compilerArgs << "-Xlint:all" << "-Werror"
+     options.errorprone.errorproneArgs.add("-XepDisableWarningsInGeneratedCode")
+     options.errorprone.errorproneArgs.addAll("-Xep:NullAway:ERROR", "-XepOpt:NullAway:AnnotatedPackages=com.uber")
  }
```

The next (optional) step would be to move to using the DSL:
```diff
  tasks.withType(JavaCompile).configureEach {
      options.compilerArgs << "-Xlint:all" << "-Werror"
-     options.errorprone.errorproneArgs.add("-XepDisableWarningsInGeneratedCode")
-     options.errorprone.errorproneArgs.addAll("-Xep:NullAway:ERROR", "-XepOpt:NullAway:AnnotatedPackages=com.uber")
+     options.errorprone {
+         disableWarningsInGeneratedCode = true
+         error("NullAway")
+         option("NullAway:AnnotatedPackages", "net.ltgt")
+     }
  }
```

Finally, the `net.ltgt.errorprone-base` plugin is removed without replacement.
In most cases, it can be replaced by disabling or enabling Error Prone on selected tasks.

[versions 0.0._x_]: https://github.com/tbroyer/gradle-errorprone-plugin-v0.0.x

</details>

## Custom Error Prone checks

*This requires Error Prone 2.3.0 or later.*

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
tasks.withType(JavaCompile).configureEach {
    options.errorprone {
        option("NullAway:AnnotatedPackages", "net.ltgt")
    }
}
tasks.named("compileJava").configure {
    // The check defaults to a warning, bump it up to an error for the main sources
    options.errorprone.error("NullAway")
}
```
<details>
<summary>with Kotlin DSL</summary>

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        option("NullAway:AnnotatedPackages", "net.ltgt")
    }
}
tasks.named("compileJava", JavaCompile::class) {
    // The check defaults to a warning, bump it up to an error for the main sources
    options.errorprone.error("NullAway")
}
```

</details>

[custom checks]: https://errorprone.info/docs/plugins

## Configuration

As noted above, this plugin adds an `errorprone` extension to the `JavaCompile.options`.
It can be configured either as a property (`options.errorprone.xxx = …`)
or script block (`options.errorprone { … }`).

In a `*.gradle.kts` script, the Kotlin extensions need to be imported:
```kotlin
import net.ltgt.gradle.errorprone.errorprone
```

### Properties

_Please note that all properties are [lazy](https://docs.gradle.org/current/userguide/lazy_configuration.html#lazy_properties),
so while you can use `=` in place of `.set(…)` in the Groovy DSL,
you cannot use `<<` or `+=` to add to lists for instance._

| Property | Description
| :------- | :----------
| `enabled`                        | (`isEnabled` with Kotlin DSL) Allows disabling Error Prone altogether for the task. Error Prone will still be in the annotation processor path, but `-Xplugin:ErrorProne` won't be passed as a compiler argument. Defaults to `true` for source set tasks, `false` otherwise.
| `disableAllChecks`               | Disable all Error Prone checks; maps to `-XepDisableAllChecks`. This will be the first argument, so checks can then be re-enabled on a case-by-case basis. Defaults to `false`.
| `disableAllWarnings`             | Maps to `-XepDisableAllWarnings` (since ErrorProne 2.4.0). Defaults to `false`.
| `allErrorsAsWarnings`            | Maps to `-XepAllErrorsAsWarnings`. Defaults to `false`.
| `allDisabledChecksAsWarnings`    | Enables all Error Prone checks, checks that are disabled by default are enabled as warnings; maps to `-XepDisabledChecksAsWarnings`. Defaults to `false`.
| `disableWarningsInGeneratedCode` | Disables warnings in classes annotated with `javax.annotation.processing.Generated` or `@javax.annotation.Generated`; maps to `-XepDisableWarningsInGeneratedCode`. Defaults to `false`.
| `ignoreUnknownCheckNames`        | Maps to `-XepIgnoreUnknownCheckNames`. Defaults to `false`.
| `ignoreSuppressionAnnotations`   | Maps to `-XepIgnoreSuppressionAnnotations` (since Error Prone 2.3.3). Defaults to `false`.
| `compilingTestOnlyCode`          | (`isCompilingTestOnlyCode` with Kotlin DSL) Maps to `-XepCompilingTestOnlyCode`. Defaults to `false`. (defaults to `true` for a source set inferred as a test source set)
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
