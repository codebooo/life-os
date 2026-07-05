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
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8/ProGuard hardening lands in Phase 14 (§9.4); debuggable-free
            // unminified release keeps early sideload iterations simple.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
