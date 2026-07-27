import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
  alias(libs.plugins.spotless)
}

android {
  namespace = "com.pathly"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.pathly"
    minSdk = 34
    targetSdk = 36

    // CI（GitHub Actions）の run 番号から versionCode を自動採番する。
    // run_number はリポジトリ横断で単調増加するため、後にビルドしたものほど
    // versionCode が大きくなり、常に更新インストールできる。ローカルは 1。
    val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
    versionCode = 1 + ciRunNumber
    versionName = if (ciRunNumber > 0) "1.0.$ciRunNumber" else "1.0"

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

  signingConfigs {
    // CI とローカルで同一のデバッグ鍵を使い、実行ごとに署名が変わらないようにする。
    // デバッグ鍵はパスワードが公知（android）で秘密情報ではないためリポジトリに含める。
    getByName("debug") {
      storeFile = file("pathly-debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
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

  // Room のスキーマ JSON を instrumented test（MigrationTestHelper）から読めるように
  // assets へ含める。出力先は下の ksp{} で指定した $projectDir/schemas。
  sourceSets {
    getByName("androidTest") {
      assets.directories.add("$projectDir/schemas")
    }
  }
}

// Room のスキーマを $projectDir/schemas に書き出す（exportSchema=true と対で使う）。
// マイグレーションの自動検証（MigrationTestHelper）とスキーマ差分レビューのため。
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
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
  ksp(libs.androidx.room.compiler)

  // Hilt
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  ksp(libs.hilt.compiler)

  // Location Services
  implementation(libs.play.services.location)

  // Maps
  implementation(libs.play.services.maps)
  implementation(libs.maps.compose)

  // Places (立ち寄り場所の命名)
  implementation(libs.places)

  // Permissions
  implementation(libs.accompanist.permissions)

  // Coroutines
  implementation(libs.kotlinx.coroutines.android)

  // Security - Encrypted SharedPreferences
  implementation(libs.androidx.security.crypto)

  testImplementation(libs.junit)

  // Unit Test dependencies
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.core.testing)
  testImplementation(libs.mockk)
  testImplementation(libs.turbine)

  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)

  // Android Integration Test dependencies
  androidTestImplementation(libs.androidx.core.testing)
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.room.testing)

  // UI Test dependencies
  androidTestImplementation(libs.mockk.android)

  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}

spotless {
  kotlin {
    target("**/*.kt")
    ktlint("1.5.0").editorConfigOverride(
      mapOf(
        "indent_size" to "2",
        // @Composable関数はPascalCaseが慣例のため命名規則の対象外にする
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
      ),
    )
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktlint("1.5.0").editorConfigOverride(
      mapOf(
        "indent_size" to "2",
      ),
    )
  }
}
