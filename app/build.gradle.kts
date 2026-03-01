import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.appdistribution)
}

val variantsConfigFile = file("../variants-config.json")
val variantsConfig = groovy.json.JsonSlurper().parse(variantsConfigFile) as Map<String, Any>

android {
  namespace = "cn.gekal.android.myapplicationwebviewinteractionsample"
  compileSdk = 36

  defaultConfig {
    applicationId = "cn.gekal.android.myapplicationwebviewinteractionsample"
    minSdk = 34
    targetSdk = 36
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
    val debugConfig = variantsConfig["debug"] as Map<String, Any>
    debug {
      buildConfigField(
        "String",
        "WEBVIEW_URL",
        "\"${debugConfig["webview_url"]}\"",
      )
    }

    val releaseConfig = variantsConfig["release"] as Map<String, Any>
    release {
      buildConfigField(
        "String",
        "WEBVIEW_URL",
        "\"${releaseConfig["webview_url"]}\"",
      )
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
      val buildTime = SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault()).format(Date())
      versionNameSuffix = "-release-$buildTime"
      firebaseAppDistribution {
        groups = releaseConfig["firebase_app_distribution_groups"] as String
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

  buildFeatures {
    compose = true
    buildConfig = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
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
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
      ),
    )
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    ktlint()
  }
}
