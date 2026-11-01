import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// google-services.json is per-developer and is never committed. Without it the
// project still builds and tests run; the app shows a "Firebase not configured"
// notice instead of the sign-in button.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
}

// Release signing values come from keystore.properties (git-ignored) or from
// environment variables in CI. Nothing secret lives in this file.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: providers.environmentVariable(env).orNull

val releaseStoreFile = signingValue("storeFile", "TICKTODO_KEYSTORE_FILE")
val releaseSigningAvailable = releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()

// A release artefact without Firebase config or a signing key is never what anyone
// wants to upload, so the build stops instead of quietly producing one. CI that has
// no secrets passes -Pticktodo.allowIncompleteRelease=true to check compilation only.
val releaseRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
if (releaseRequested && !gradleProp("ticktodo.allowIncompleteRelease", "false").toBoolean()) {
    val missing = buildList {
        if (!hasFirebaseConfig) add("app/google-services.json (sign-in and sync would be disabled)")
        if (!releaseSigningAvailable) add("release signing (keystore.properties or TICKTODO_KEYSTORE_* variables; see docs/release/signing.md)")
    }
    if (missing.isNotEmpty()) {
        error("Release build refused, missing: ${missing.joinToString("; ")}. Pass -Pticktodo.allowIncompleteRelease=true to build anyway.")
    }
}

fun gradleProp(name: String, default: String = ""): String =
    providers.gradleProperty(name).orNull ?: default

android {
    namespace = "app.ticktodo"
    compileSdk = 37

    defaultConfig {
        applicationId = gradleProp("ticktodo.applicationId")
        minSdk = 26
        targetSdk = 36
        versionCode = gradleProp("ticktodo.versionCode", "1").toInt()
        versionName = gradleProp("ticktodo.versionName", "1.0.0")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${gradleProp("ticktodo.privacyPolicyUrl")}\"")
        buildConfigField("String", "TERMS_URL", "\"${gradleProp("ticktodo.termsUrl")}\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"${gradleProp("ticktodo.supportEmail")}\"")
        buildConfigField("boolean", "ADS_ENABLED", "false")
        buildConfigField("boolean", "FIREBASE_CONFIGURED", hasFirebaseConfig.toString())
        buildConfigField("String", "EMULATOR_HOST", "\"${gradleProp("ticktodo.emulatorHost", "10.0.2.2")}\"")
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "TICKTODO_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "TICKTODO_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "TICKTODO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Point debug builds at the local Firebase emulators with
            // -Pticktodo.useEmulator=true (see README).
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", gradleProp("ticktodo.useEmulator", "false"))
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "false")
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    // The exported Room schemas double as assets for the migration test. Only the
    // debug variant (which unit tests run against) gets them; release never ships them.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        // Version-bump suggestions are handled by the dependency review, not lint.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*.md")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.profileinstaller)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.viewmodel.compose)
    ksp(libs.androidx.hilt.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
