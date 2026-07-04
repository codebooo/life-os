plugins {
    alias(libs.plugins.lifeos.android.library)
    alias(libs.plugins.lifeos.hilt)
}

dependencies {
    api(projects.core.common)
    implementation(libs.androidx.datastore.preferences)
}
