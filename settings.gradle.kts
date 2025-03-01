pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }
}

rootProject.name = "gradle-errorprone-plugin"

dependencyResolutionManagement {
    rulesMode = RulesMode.FAIL_ON_PROJECT_RULES
    components {
        withModule("com.google.truth:truth") {
            withVariant("compile") {
                withDependencies {
                    // junit is actually a runtime-only dependency
                    // See https://github.com/google/truth/issues/333
                    removeAll { it.group == "junit" }
                }
            }
        }
    }
}
