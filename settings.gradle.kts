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
        mavenCentral()
    }
}

// The Tether SDK is not on Maven Central yet, so Gradle builds it from source.
// The SDK's build adds an Android module when it finds an Android SDK; this app
// only needs the JVM parts, so skip it (no Google Maven, faster first build).
System.setProperty("org.gradle.project.tether.android", "false")

val localSdk = providers.gradleProperty("tether.sdkPath").orNull
if (localSdk != null) {
    // -Ptether.sdkPath=../tether-xmpp builds against your own checkout of the SDK.
    includeBuild(localSdk)
} else {
    // Otherwise Gradle clones github.com/usmandurrany/tether-xmpp on the first build
    // (into .gradle/vcs-1) and compiles it. The branch is set in build.gradle.kts.
    sourceControl {
        gitRepository(uri("https://github.com/usmandurrany/tether-xmpp.git")) {
            producesModule("io.github.usmandurrany.tether:tether-core")
            producesModule("io.github.usmandurrany.tether:tether-im")
            producesModule("io.github.usmandurrany.tether:tether-debug")
        }
    }
}
