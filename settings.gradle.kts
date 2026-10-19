rootProject.name = "tether-demo-desktop"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // A prebuilt copy of the Tether SDK (JVM artifacts), kept in this repository
        // because the SDK is not on Maven Central yet. Refresh it with scripts/update-sdk.
        maven {
            name = "tetherSdk"
            url = uri("sdk-repo")
            content { includeGroup("io.github.usmandurrany.tether") }
        }
        // Use -Ptether.mavenLocal to build against `./gradlew publishToMavenLocal` of an SDK checkout instead.
        if (providers.gradleProperty("tether.mavenLocal").isPresent) {
            mavenLocal { content { includeGroup("io.github.usmandurrany.tether") } }
        }
        mavenCentral()
    }
}
