plugins {
    alias(libs.plugins.lifeos.android.library)
    alias(libs.plugins.lifeos.hilt)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.ai)
    implementation(projects.core.database)
    implementation(projects.core.service)

    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
