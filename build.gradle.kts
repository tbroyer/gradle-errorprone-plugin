import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.9.10"
}

group = "net.ltgt.gradle"

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    // This is the version used in Gradle 4.6, for backwards compatibility when we'll upgrade
    kotlinOptions.apiVersion = "1.2"
}

gradle.taskGraph.whenReady {
    val publishPlugins by tasks.getting
    if (hasTask(publishPlugins)) {
        check("git diff --quiet --exit-code".execute(null, rootDir).waitFor() == 0, { "Working tree is dirty" })
        val process = "git describe --exact-match".execute(null, rootDir)
        check(process.waitFor() == 0, { "Version is not tagged" })
        version = process.text.trim().removePrefix("v")
    }
}

val errorproneVersion = "2.3.1"
val androidPluginVersion = "3.1.1"

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
    testImplementation("com.google.truth:truth:0.40")
    testImplementation("com.google.errorprone:error_prone_check_api:$errorproneVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.allWarningsAsErrors = true
}

val compileKotlin by tasks.getting(KotlinCompile::class) {
    kotlinOptions.allWarningsAsErrors = true
}

// See https://github.com/gradle/kotlin-dsl/issues/492
publishing {
    repositories {
        maven(url = "$buildDir/repository") {
            name = "test"
        }
    }
}
val publishPluginsToTestRepository by tasks.creating {
    dependsOn("publishPluginMavenPublicationToTestRepository")
    dependsOn("publishErrorprone-javacpluginPluginMarkerMavenPublicationToTestRepository")
}

val test by tasks.getting(Test::class) {
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
    systemProperty("android-plugin.version", androidPluginVersion)

    if (project.findProperty("test.skipAndroid")?.toString()?.toBoolean() == true) {
        exclude("**/*Android*")
    }

    testLogging {
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
    }
}

gradlePlugin {
    (plugins) {
        "errorprone-javacplugin" {
            id = "net.ltgt.errorprone-javacplugin"
            implementationClass = "net.ltgt.gradle.errorprone.javacplugin.ErrorProneJavacPluginPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/tbroyer/gradle-errorprone-javacplugin-plugin"
    vcsUrl = "https://github.com/tbroyer/gradle-errorprone-javacplugin-plugin"
    description = "Gradle plugin to use the error-prone compiler for Java (as a javac plugin)"
    tags = listOf("javac", "error-prone")

    (plugins) {
        "errorprone-javacplugin" {
            id = "net.ltgt.errorprone-javacplugin"
            displayName = "Gradle error-prone plugin (as a javac plugin)"
        }
    }

    mavenCoordinates {
        groupId = project.group.toString()
        artifactId = project.name
    }
}

val ktlint by configurations.creating

dependencies {
    ktlint("com.github.shyiko:ktlint:0.22.0")
}

val verifyKtlint by tasks.creating(JavaExec::class) {
    description = "Check Kotlin code style."
    classpath = ktlint
    main = "com.github.shyiko.ktlint.Main"
    args("**./*.gradle.kts", "**/*.kt")
}
tasks["check"].dependsOn(verifyKtlint)

task("ktlint", JavaExec::class) {
    description = "Fix Kotlin code style violations."
    classpath = verifyKtlint.classpath
    main = verifyKtlint.main
    args("-F")
    args(verifyKtlint.args)
}

fun String.execute(envp: Array<String>?, workingDir: File?) =
    Runtime.getRuntime().exec(this, envp, workingDir)

val Process.text: String
    get() = inputStream.bufferedReader().readText()
