// Top-level imports
import org.jetbrains.dokka.gradle.DokkaTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.jetbrains.kotlin.serialization)
    id("org.jetbrains.dokka") version "1.8.20"
    `maven-publish`
}

android {
    namespace = "net.locationstation.elevenlabs.ws"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    testOptions {
        unitTests {
            // Allows JVM unit tests to call into Android types without “Method … not mocked.”
            isReturnDefaultValues = true

            // Enable for Robolectric to access Android resources
            isIncludeAndroidResources = true
        }
    }

    // ← Align Java compilation to 1.8
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        // This controls the bytecode level Kotlin emits.
        jvmTarget = "17"
    }

    buildTypes {
        release {
            // preserve debug symbols / line numbers
            isMinifyEnabled = false
        }
    }

    // only publish the release AAR
    publishing {
        singleVariant("release")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Core Kotlin & Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")


    // Networking with OkHttp
    implementation(libs.squareup.okhttp)
    implementation(libs.squareup.logging.interceptor)

    // JSON Parsing
    implementation(libs.kotlinx.serialization.json)
    
    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.annotation:annotation:1.7.1")

    // Testing - Unit Tests
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit")) // Use the kotlin function for test dependencies
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.robolectric:robolectric:4.11.1")
    
    // Testing - Android Tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}


// 1) Sources JAR
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from("src/main/java")
    from("src/main/kotlin")
}

// 2) Dokka HTML output
val dokkaOutputDir = layout.buildDirectory.dir("dokka")

tasks.named<org.jetbrains.dokka.gradle.DokkaTask>("dokkaHtml") {
    // no deprecation warning here
    outputDirectory.set(dokkaOutputDir)
}

// 3) Javadoc
val javadocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.named("dokkaHtml"))
    archiveClassifier.set("javadoc")

    // you can pass the provider directly
    from(dokkaOutputDir)
}

val releaseAar = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar")


// 4) Maven-Publish configuration
publishing {
    publications {
        create<MavenPublication>("release") {
            groupId    = "net.locationstation"
            artifactId = "elevenlabs-ws-kt"
            version    = "1.0.0-SNAPSHOT"

            // a) main AAR
            artifact(releaseAar)
            // b) sources & javadoc
            artifact(sourcesJar.get())
            artifact(javadocJar.get())

            // c) nice-to-have POM metadata
            pom {
                name.set("ElevenLabs WebSocket Kotlin Client")
                description.set("A Kotlin library for the ElevenLabs Conversational AI WebSocket API.")
                url.set("https://github.com/location-station/elevenlabs-ws-kt")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("dlevine")
                        name.set("David Levine")
                        email.set("david@locationstation.net")
                        organization.set("Location Station")
                        organizationUrl.set("https://www.locationstation.net")
                    }
                }
                scm {
                    connection.set("scm:git:github.com/location-station/elevenlabs-ws-kt.git")
                    developerConnection.set("scm:git:ssh://github.com/location-station/elevenlabs-ws-kt.git")
                    url.set("https://github.com/location-station/elevenlabs-ws-kt/tree/main")
                }
            }
        }
    }
    repositories {
        mavenLocal()
    }
}
