plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

group = "io.github.usmandurrany.tether.demo"
version = "0.1.0"

// Java 17 bytecode, built with whatever JDK 17+ runs Gradle.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Built from the SDK's GitHub repository (see settings.gradle.kts).
    // Pass -Ptether.branch=<branch> to try another branch of the SDK.
    val sdkBranch = providers.gradleProperty("tether.branch").getOrElse("main")
    for (sdk in listOf(libs.tether.core, libs.tether.im, libs.tether.debug)) {
        implementation(sdk) { version { branch = sdkBranch } }
    }
    // Optional: lets TLS 1.3 logins use the stronger tls-exporter channel binding.
    // Without it the JDK's TLS is used with tls-server-end-point.
    runtimeOnly(libs.conscrypt.openjdk)

    // Compose for Desktop: the UI toolkit (with Skia for this OS), Material 3, and ViewModel.
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.viewmodel.compose)
    // Dispatchers.Main on the desktop (the AWT event thread), which viewModelScope runs on.
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(kotlin("test"))
}

val mainClassName = "io.github.usmandurrany.tether.demo.MainKt"

compose.desktop {
    application {
        mainClass = mainClassName
        // `./gradlew run -Pdemo.home=/tmp/bob` keeps this run's files in another folder,
        // for example to run two accounts side by side.
        providers.gradleProperty("demo.home").orNull?.let { jvmArgs("-Dtether.demo.home=$it") }
        nativeDistributions {
            // `./gradlew packageDistributionForCurrentOS` builds an installer for this OS.
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "tether-demo"
            packageVersion = "1.0.0"
            modules("java.naming", "jdk.crypto.ec")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // ScreenshotTest draws off-screen; it needs no display.
    systemProperty("java.awt.headless", "true")
}

// A single runnable jar for this OS: `./gradlew packageJar`, then `java -jar build/libs/tether-demo-desktop-<version>-all.jar`.
tasks.register<Jar>("packageJar") {
    group = "distribution"
    description = "Builds one runnable jar with every dependency inside."
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes("Main-Class" to mainClassName) }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/9/module-info.class", "module-info.class")
}
