// buildSrc is an independent Gradle build. Its Kotlin DSL plugin dependencies
// must resolve from Maven Central as well as the Gradle plugin portal.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
