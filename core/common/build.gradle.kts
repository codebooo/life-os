plugins {
    alias(libs.plugins.lifeos.android.library)
    alias(libs.plugins.lifeos.hilt)
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.turbine)
}
