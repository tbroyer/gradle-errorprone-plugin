import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.10.0"
}

group = "net.ltgt.gradle"

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

tasks.withType<KotlinCompile>().configureEach {
    // This is the version used in Gradle 4.6, for backwards compatibility when we'll upgrade
    kotlinOptions.apiVersion = "1.2"

    kotlinOptions.allWarningsAsErrors = true
}

gradle.taskGraph.whenReady {
    if (hasTask(":publishPlugins")) {
        check("git diff --quiet --exit-code".execute(null, rootDir).waitFor() == 0) { "Working tree is dirty" }
        val process = "git describe --exact-match".execute(null, rootDir)
        check(process.waitFor() == 0) { "Version is not tagged" }
        version = process.text.trim().removePrefix("v")
    }
}

val errorproneVersion = "2.3.1"
val errorproneJavacVersion = "9+181-r4173-1"
val androidPluginVersion = "3.1.4"

repositories {
    jcenter()
    google()
}
dependencies {
    compileOnly("com.android.tools.build:gradle:$androidPluginVersion") {
        exclude(module = "kotlin-stdlib-jre8").because(
            "kotlin-stdlib-jreN is deprecated, " +
                "it's safe to exclude because we have kotlin-stdlib-jdk8 in the classpath through the `embedded-kotlin` plugin"
        )
    }
    testRuntimeOnly("com.android.tools.build:gradle:$androidPluginVersion")

    testImplementation("junit:junit:4.12")
    testImplementation("com.google.truth:truth:0.42")
    testImplementation("com.google.errorprone:error_prone_check_api:$errorproneVersion")
}

// See https://github.com/gradle/kotlin-dsl/issues/492
publishing {
    repositories {
        maven(url = "$buildDir/repository") {
            name = "test"
        }
    }
}

tasks {
    val publishPluginsToTestRepository by registering {
        dependsOn("publishPluginMavenPublicationToTestRepository")
        dependsOn("publishErrorpronePluginMarkerMavenPublicationToTestRepository")
        dependsOn("publishErrorprone-basePluginMarkerMavenPublicationToTestRepository")
        dependsOn("publishErrorprone-javacpluginPluginMarkerMavenPublicationToTestRepository")
    }

    "test"(Test::class) {
        dependsOn(publishPluginsToTestRepository)

        val testJavaHome = project.findProperty("test.java-home")
        testJavaHome?.also { systemProperty("test.java-home", it) }

        val testGradleVersion = project.findProperty("test.gradle-version")
        testGradleVersion?.also { systemProperty("test.gradle-version", testGradleVersion) }

        val androidSdkHome = project.findProperty("test.android-sdk-home")
            ?: System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME")
        androidSdkHome?.also { systemProperty("test.android-sdk-home", androidSdkHome) }

        systemProperty("plugin.version", version)
        systemProperty("errorprone.version", errorproneVersion)
        systemProperty("errorprone-javac.version", errorproneJavacVersion)
        systemProperty("android-plugin.version", androidPluginVersion)

        if (project.findProperty("test.skipAndroid").toString().toBoolean()) {
            exclude("**/*Android*")
        }

        testLogging {
            showExceptions = true
            showStackTraces = true
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

gradlePlugin {
    plugins {
        register("errorprone") {
            id = "net.ltgt.errorprone"
            displayName = "Gradle error-prone plugin"
            implementationClass = "net.ltgt.gradle.errorprone.ErrorPronePlugin"
        }
        register("errorprone-base") {
            id = "net.ltgt.errorprone-base"
            displayName = "Gradle error-prone base plugin"
            implementationClass = "net.ltgt.gradle.errorprone.ErrorProneBasePlugin"
        }
        register("errorprone-javacplugin") {
            id = "net.ltgt.errorprone-javacplugin"
            displayName = "Gradle error-prone plugin (as a javac plugin)"
            implementationClass = "net.ltgt.gradle.errorprone.ErrorProneJavacPluginPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/tbroyer/gradle-errorprone-plugin"
    vcsUrl = "https://github.com/tbroyer/gradle-errorprone-plugin"
    description = "Gradle plugin to use the error-prone compiler for Java"
    tags = listOf("javac", "error-prone")

    mavenCoordinates {
        groupId = project.group.toString()
        artifactId = project.name
    }
}

val ktlint by configurations.creating

dependencies {
    ktlint("com.github.shyiko:ktlint:0.27.0")
}

tasks {
    val verifyKtlint by registering(JavaExec::class) {
        description = "Check Kotlin code style."
        classpath = ktlint
        main = "com.github.shyiko.ktlint.Main"
        args("**/*.gradle.kts", "**/*.kt")
    }
    "check" {
        dependsOn(verifyKtlint)
    }

    register("ktlint", JavaExec::class.java) {
        description = "Fix Kotlin code style violations."
        classpath = ktlint
        main = "com.github.shyiko.ktlint.Main"
        args("-F", "**/*.gradle.kts", "**/*.kt")
    }
}

fun String.execute(envp: Array<String>?, workingDir: File?) =
    Runtime.getRuntime().exec(this, envp, workingDir)

val Process.text: String
    get() = inputStream.bufferedReader().readText()
