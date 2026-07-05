plugins {
    alias(libs.plugins.lifeos.android.library)
    alias(libs.plugins.lifeos.hilt)
}

dependencies {
    implementation(projects.core.common)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
}
