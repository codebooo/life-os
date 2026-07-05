plugins {
    `kotlin-dsl`
}

group = "com.lifeos.buildlogic"

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    // Applied by plugin id only (no classes referenced), so runtime classpath is
    // enough — and it keeps their Kotlin-2.3 metadata off the compile classpath,
    // which Gradle's embedded Kotlin cannot read.
    runtimeOnly(libs.compose.compiler.gradle.plugin)
    runtimeOnly(libs.ksp.gradle.plugin)
    runtimeOnly(libs.hilt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "lifeos.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "lifeos.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "lifeos.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "lifeos.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "lifeos.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
