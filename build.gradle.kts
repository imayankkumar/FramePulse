// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    // Checks the library's public API against framepulse/api/framepulse.api on every build.
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
}

apiValidation {
    // The demo app is not a published API.
    ignoredProjects += "app"
}
