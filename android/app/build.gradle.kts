import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.hilt)
  alias(libs.plugins.kotlin.kapt)
  id("com.diffplug.spotless")
}

android {
  namespace = "com.pathly"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.pathly"
    minSdk = 34
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // local.propertiesからGoogle Maps APIキーを読み込み
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
      localProperties.load(localPropertiesFile.inputStream())
    }

    // AndroidManifest.xmlのプレースホルダーに値を注入
    manifestPlaceholders["GOOGLE_MAPS_API_KEY"] =
      localProperties.getProperty("GOOGLE_MAPS_API_KEY", "")

    // BuildConfigにAPIキーを埋め込み（オプション）
    buildConfigField(
      "String",
      "GOOGLE_MAPS_API_KEY",
      "\"${localProperties.getProperty("GOOGLE_MAPS_API_KEY", "")}\"",
    )
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true // BuildConfig生成を有効化
  }

  packaging {
    resources {
      excludes += "META-INF/LICENSE.md"
      excludes += "META-INF/LICENSE-notice.md"
    }
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
    }
  }
}

dependencies {

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)

  // Room
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  kapt(libs.androidx.room.compiler)

  // Hilt
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  kapt(libs.hilt.compiler)

  // Location Services
  implementation(libs.play.services.location)

  // Maps
  implementation(libs.play.services.maps)
  implementation(libs.maps.compose)

  // Permissions
  implementation(libs.accompanist.permissions)

  // Coroutines
  implementation(libs.kotlinx.coroutines.android)

  // Security - Encrypted SharedPreferences
  implementation("androidx.security:security-crypto:1.1.0-alpha06")

  testImplementation(libs.junit)

  // Unit Test dependencies
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
  testImplementation("androidx.arch.core:core-testing:2.2.0")
  testImplementation("io.mockk:mockk:1.13.8")
  testImplementation("app.cash.turbine:turbine:1.0.0")

  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)

  // Android Integration Test dependencies
  androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
  androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
  androidTestImplementation("androidx.room:room-testing:2.6.1")

  // UI Test dependencies
  androidTestImplementation("io.mockk:mockk-android:1.13.8")
  androidTestImplementation("androidx.compose.ui:ui-test-manifest")

  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}

spotless {
  kotlin {
    target("**/*.kt")
    ktlint("0.50.0").editorConfigOverride(
      mapOf(
        "indent_size" to "2",
      ),
    )
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktlint("0.50.0").editorConfigOverride(
      mapOf(
        "indent_size" to "2",
      ),
    )
  }
}
