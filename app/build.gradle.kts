@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.rikka.tools.refine)
}

android {
    namespace = "com.jtech.zemer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jtech.zemer"
        minSdk = 26
        targetSdk = 36
        versionCode = 35
        versionName = "35"
        buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        // Git commit of this build — the nightly-updater's identity (every main build shares the
        // same versionName, so "is a newer nightly available" is a SHA comparison, not a version
        // one). Empty when git is unavailable, which makes any nightly count as an update.
        val commitHash = runCatching {
            providers.exec { commandLine("git", "rev-parse", "HEAD") }
                .standardOutput.asText.get().trim()
        }.getOrDefault("")
        buildConfigField("String", "COMMIT_HASH", "\"$commitHash\"")
        val googleTokenExchangeUrl = (project.findProperty("googleTokenExchangeUrl") as String?) ?: ""
        buildConfigField("String", "GOOGLE_TOKEN_EXCHANGE_URL", "\"$googleTokenExchangeUrl\"")
        // Read-only content mirror (content.zemer.io) used mirror-first with the Firebase SDK as
        // fallback (see ZemerContentClient). Override with -PcontentMirrorUrl=; empty disables the
        // mirror so every content read goes straight to Firebase (debug force-Firebase / A-B).
        val contentMirrorUrl = (project.findProperty("contentMirrorUrl") as String?) ?: "https://content.zemer.io"
        buildConfigField("String", "CONTENT_MIRROR_URL", "\"$contentMirrorUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // NDK r27 needs this for 16 KB page-size ELF alignment (default from r28)
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    androidResources {
        localeFilters += listOf("en", "iw")
    }

    signingConfigs {
        create("persistentDebug") {
            storeFile = file("persistent-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("keystore/release.keystore")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
            storeType = "PKCS12"
        }
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
            }
        }
        debug {
            isDebuggable = true
            signingConfig = if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("persistentDebug")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable"
        )
    }

    androidResources {
        generateLocaleConfig = true
    }

    // ABI splits disabled - builds single universal APK
    // Enable if you want smaller per-architecture APKs for distribution
    // splits {
    //     abi {
    //         isEnable = true
    //         reset()
    //         include("arm64-v8a", "armeabi-v7a")
    //         isUniversalApk = true
    //     }
    // }

    // Skip native build when using prebuilt libs (CI sets USE_PREBUILT_NATIVE=true)
    if (System.getenv("USE_PREBUILT_NATIVE") != "true") {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
        ndkVersion = "27.0.12077973"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            pickFirsts += "**/libcoverart.so"
            // The FCast sender-SDK native lib is NOT bundled (~5.3 MB) — it is downloaded on demand from
            // ZemerTeam/zemer-cast when the user enables casting (see CastNativeLibLoader).
            excludes += "**/libfcast_sender_sdk.so"
            keepDebugSymbols += listOf(
                "**/libandroidx.graphics.path.so",
                "**/libdatastore_shared_counter.so",
                "**/libcoverart.so"
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.all {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn"
        )
        suppressWarnings.set(false)
    }
}

dependencies {
    implementation("com.zemer:cipher")

    implementation(libs.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(libs.materialKolor)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    // SVG decode for the server-generated curated-playlist covers (/zemer-playlists/cover).
    implementation(libs.coil.svg)

    implementation(libs.ucrop)

    implementation(libs.shimmer)
    implementation(libs.lottie.compose)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.compose.video)
    implementation(libs.squigglyslider)

    implementation(libs.room.runtime)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.crashlytics.ndk)
    implementation(libs.firebase.analytics)
    implementation(libs.play.services.auth)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(project(":innertube"))
    implementation(project(":lrclib"))
    implementation(project(":simpmusic"))

    // No external dependencies for cover art - using native Bento4 library

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.serialization.json)
    // Music recognition: standalone ktor client (CIO) talking to the Shazam discovery endpoint
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    // Gzip for the content mirror (ZemerContentClient): the /whitelist payload is ~432 KB raw / ~70 KB gzipped.
    implementation(libs.ktor.client.encoding)

    // Self-update installers (Shizuku / root); hidden PackageInstaller APIs via refine
    compileOnly(libs.rikka.hidden.stub)
    implementation(libs.rikka.tools.refine.runtime)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.lsposed.hiddenapibypass)
    implementation(libs.libsu.core)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)

    testImplementation(libs.junit)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation("org.futo.gitlab.videostreaming.fcast-sdk-jitpack:sender-sdk-minimal:0.4.0") {
        exclude(group = "net.java.dev.jna")
    }
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
