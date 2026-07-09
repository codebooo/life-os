plugins {
    alias(libs.plugins.lifeos.android.application)
    alias(libs.plugins.lifeos.android.compose)
    alias(libs.plugins.lifeos.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.lifeos.app"

    defaultConfig {
        applicationId = "com.lifeos"
        versionCode = 13
        versionName = "0.1.0-alpha.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // S22 Ultra target (§ target device); drops non-arm64 native libs from the APK.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            // Personal sideload keystore (§9.4): committed on purpose so updates
            // never wipe the DB/Vault. Not a secret in the Play-Store sense —
            // this app is never published to a store.
            storeFile = rootProject.file("release.keystore")
            storePassword = "lifeos-alpha"
            keyAlias = "lifeos"
            keyPassword = "lifeos-alpha"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Jakarta Mail stack ships duplicate notices; keep service files (§9.4).
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md"
        }
    }
}

dependencies {
    implementation(projects.feature.chat)
    implementation(projects.feature.capture)
    implementation(projects.feature.notes)
    implementation(projects.feature.reminders)
    implementation(projects.feature.todo)
    implementation(projects.feature.calendar)
    implementation(projects.feature.messagecenter)
    implementation(projects.feature.dhl)
    implementation(projects.feature.imagereasoning)
    implementation(projects.feature.finance)
    implementation(projects.feature.email)
    implementation(projects.feature.nas)
    implementation(projects.feature.books)
    implementation(projects.feature.route)
    implementation(projects.feature.smarthome)
    implementation(projects.feature.planner)
    implementation(projects.feature.clock)
    implementation(projects.feature.adhd)
    implementation(projects.feature.memex)
    implementation(projects.feature.agentic)
    implementation(projects.feature.evolution)

    implementation(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.ai)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.vault)
    implementation(projects.core.service)
    implementation(projects.core.ui)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
