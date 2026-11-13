plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from environment variables (set by GitHub Actions).
// Local builds without a keystore fall back to the debug key; on CI a missing key fails the build
// so a debug-signed APK is never published as a release.
val releaseKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() && file(it).exists() }
val isCi: Boolean = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
val releaseSigningComplete: Boolean = releaseKeystorePath != null &&
    listOf("KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD").all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "com.vknxworks.mapsctrl"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vknxworks.mapsctrl"
        minSdk = 35
        targetSdk = 36
        // Bump both to publish a new release (see CHANGELOG.md).
        versionCode = 5
        versionName = "0.4.0"
        // 64-bit ARM phones only (arm64-v8a), like every Ctrl app.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // -PreleaseTest: a minified build that installs next to the real app, for testing R8 on a phone.
            if (project.hasProperty("releaseTest")) applicationIdSuffix = ".releasetest"
            signingConfig = when {
                releaseSigningComplete -> signingConfigs.getByName("release")
                isCi -> throw GradleException(
                    "Release signing is not configured on CI (keystore, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD). " +
                        "Refusing to sign the release with the debug key.",
                )
                else -> signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST")
    }

    // F-Droid: no dependency-info block in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.all {
            // The bundled BRouter profiles, and (optionally) a folder with .rd5 segments for the routing test.
            it.systemProperty("mapsctrl.profiles", file("src/main/assets/brouter").absolutePath)
            System.getenv("MAPSCTRL_TEST_SEGMENTS")?.let { dir -> it.systemProperty("mapsctrl.segments", dir) }
            // A Mapsforge .map file (monaco.map) for the street-name lookup test.
            System.getenv("MAPSCTRL_TEST_MAP")?.let { map -> it.systemProperty("mapsctrl.map", map) }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.ui.text.ExperimentalTextApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

// Google-free: no Google library may reach the APK, not even transitively. AndroidX's
// concurrent-futures pulls Guava's one-interface listenablefuture jar, and profileinstaller
// (baseline-profile installation, not needed for an offline app) pulls concurrent-futures.
configurations.configureEach {
    exclude(group = "com.google.guava")
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

dependencies {
    implementation(project(":brouter"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Offline vector maps (LGPL-3.0).
    implementation(libs.mapsforge.map.android)
    implementation(libs.mapsforge.map.reader)

    // SQLite with R-tree and FTS5 for the POI search files (Android's own SQLite has neither).
    implementation(libs.androidx.sqlite.bundled)

    testImplementation(libs.junit)
}
