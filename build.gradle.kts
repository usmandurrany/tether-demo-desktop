plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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
    val sdkBranch = providers.gradleProperty("tether.branch").getOrElse("claude/project-thread-dgb99o") // back to "main" once the SDK's profiles PR merges
    for (sdk in listOf(libs.tether.core, libs.tether.im, libs.tether.debug)) {
        implementation(sdk) { version { branch = sdkBranch } }
    }
    // Optional: lets TLS 1.3 logins use the stronger tls-exporter channel binding.
    // Without it the JDK's TLS is used with tls-server-end-point.
    runtimeOnly(libs.conscrypt.openjdk)

    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.flatlaf)

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.github.usmandurrany.tether.demo.MainKt")
    applicationName = "tether-demo"
}

tasks.test {
    useJUnitPlatform()
}

// A single runnable jar: `./gradlew packageJar`, then `java -jar build/libs/tether-demo-<version>-all.jar`.
tasks.register<Jar>("packageJar") {
    group = "distribution"
    description = "Builds one runnable jar with every dependency inside."
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes("Main-Class" to application.mainClass.get()) }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/9/module-info.class", "module-info.class")
}
