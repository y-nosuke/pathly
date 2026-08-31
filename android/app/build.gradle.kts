import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  alias(libs.plugins.spotless)
}

android {
  namespace = "com.pathly"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.pathly"
    minSdk = 34
    targetSdk = 37

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
}

// Room のスキーマを $projectDir/schemas に書き出す（exportSchema=true と対で使う）。
// マイグレーションの自動検証（MigrationTestHelper）とスキーマ差分レビューのため。
//
// **書き出しは Room Gradle Plugin に任せる。** KSP に `room.schemaLocation` を直接渡していた頃は
// 出力先がバリアント共通で、debug と release の KSP が**同じ JSON を一方が書いている最中に他方が
// 読み**、新しいバージョンを初めて書き出すときだけ CI が確率で落ちていた（JsonDecodingException:
// had 'EOF'）。実行順の固定で回避していたが、プラグインは KSP にバリアントごとの build 内の
// ディレクトリを渡し、`copyRoomSchemas` でここへまとめるので、そもそも重ならない。
//
// instrumented test の assets への取り込みも
// `copyRoomSchemasToAndroidTestAssets…` が面倒を見るので、sourceSets の手当ては要らない。
room {
  schemaDirectory("$projectDir/schemas")
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

  // Navigation
  implementation(libs.androidx.navigation.compose)

  // Hilt
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  ksp(libs.hilt.compiler)

  // WorkManager（オンライン復帰後の名前解決キャッチアップ）
  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)

  // Location Services
  implementation(libs.play.services.location)

  // Maps
  implementation(libs.play.services.maps)
  implementation(libs.maps.compose)

  // Places (立ち寄り場所の命名)
  implementation(libs.places)

  // Coroutines
  implementation(libs.kotlinx.coroutines.android)

  // kotlinx-serialization のバージョン統一（BOM）。
  // room-testing が json 1.8.1 を引く一方、consistent resolution で core が
  // 1.7.3 に固定され、MigrationTestHelper のスキーマ読み込みが版ずれで落ちる。
  // BOM で core/json を 1.8.1 に揃える（androidTest 側も追従する）。
  implementation(platform(libs.kotlinx.serialization.bom))

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
