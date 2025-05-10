import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.appdistribution)
}

android {
  namespace = "cn.gekal.android.myapplicationwebviewinteractionsample"
  compileSdk = 35

  defaultConfig {
    applicationId = "cn.gekal.android.myapplicationwebviewinteractionsample"
    minSdk = 34
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      storeFile = file("debug.jks")
      storePassword = "123456"
      keyAlias = "samle-sign-alias"
      keyPassword = "123456"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
      val buildTime = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault()).format(Date())
      versionNameSuffix = "-release-$buildTime"
      firebaseAppDistribution {
        groups = "gekal"
      }
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    compose = true
  }
}

dependencies {

  implementation(platform(libs.firebase.bom))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}

spotless {
  kotlin {
    target("**/*.kt")
    ktlint().editorConfigOverride(
      mapOf(
        "ktlint_experimental" to "enabled",
        "standard:no-wildcard-imports" to "disabled",
      ),
    )
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    ktlint()
  }
}
