import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.9.10"
    id("com.github.sherter.google-java-format") version "0.6"
    id("net.ltgt.errorprone") version "0.0.13"
}

googleJavaFormat {
    toolVersion = "1.5"
}

group = "net.ltgt.gradle"

check(JavaVersion.current().isJava9Compatible, { "Tests require a Java 9 compatible JDK" })
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<JavaCompile> { options.compilerArgs.addAll(arrayOf("--release", "8")) }
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

val errorproneVersion = "2.2.0"

repositories {
    jcenter()
}
dependencies {
    errorprone("com.google.errorprone:error_prone_core:$errorproneVersion")

    testImplementation("junit:junit:4.12")
    testImplementation("com.google.truth:truth:0.39")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(arrayOf("-Xlint:all", "-Werror"))
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

    val testGradleVersion = project.findProperty("test.gradle-version") ?: GradleVersion.current().version
    systemProperty("test.gradle-version", testGradleVersion)

    systemProperty("plugin.version", version)
    systemProperty("errorprone.version", errorproneVersion)

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
    ktlint("com.github.shyiko:ktlint:0.18.0")
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
