import com.android.build.gradle.internal.scope.ProjectInfo.Companion.getBaseName

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless) apply false
}

subprojects {
    plugins.apply("com.diffplug.spotless")
}