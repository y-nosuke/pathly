import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
  alias(libs.plugins.spotless)
}

/** git を実行して標準出力を返す。git が無い・失敗したときは null（ビルドは止めない）。 */
fun git(vararg args: String): String? = runCatching {
  providers.exec {
    commandLine("git", *args)
    isIgnoreExitValue = true
  }.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

/**
 * versionCode は HEAD までのコミット数。main はマージで増える一方なので、後の版ほど大きくなり
 * 常に上書きインストールできる。浅い clone では数え間違うため、CI は全履歴を取得すること（fetch-depth: 0）。
 */
fun gitVersionCode(): Int = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

/**
 * versionName は直近の `vX.Y.Z` タグから作る。
 * - タグの上: `0.3.0`
 * - タグの後: `0.3.0+dev.5.abc1234`（0.3.0 から 5 コミット先の abc1234。SemVer のビルドメタデータ）
 * - タグが無い: `0.0.0+dev.abc1234`
 */
fun gitVersionName(): String {
  val described = git("describe", "--tags", "--match", "v[0-9]*.[0-9]*.[0-9]*", "--long", "--always")
    ?: return "0.0.0+dev"
  // `v1.0.0-rc.1` のようなプレリリースのタグも受ける（`-` 区切りなので git describe の末尾と分けて読む）
  val match = Regex("""^v(\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?)-(\d+)-g([0-9a-f]+)$""").matchEntire(described)
    ?: return "0.0.0+dev.$described"
  val (version, distance, hash) = match.destructured
  return if (distance == "0") version else "$version+dev.$distance.$hash"
}

android {
  namespace = "com.pathly"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.pathly"
    minSdk = 34
    targetSdk = 37

    // バージョンは git から決める（docs/designs/release.md）。CI とローカルで同じ値になる。
    versionCode = gitVersionCode()
    versionName = gitVersionName()

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
    // リリース鍵は**リポジトリに含めない**。Gradle プロパティで渡す
    // （ローカルは ~/.gradle/gradle.properties、CI は ORG_GRADLE_PROJECT_* 環境変数）。
    // 渡されていなければ作らず、リリース版は未署名で出る（手元の build や PR の CI はそれでよい）。
    val releaseStoreFile = providers.gradleProperty("pathlyReleaseStoreFile").orNull
    if (releaseStoreFile != null) {
      create("release") {
        storeFile = file(releaseStoreFile)
        storePassword = providers.gradleProperty("pathlyReleaseStorePassword").get()
        keyAlias = providers.gradleProperty("pathlyReleaseKeyAlias").get()
        keyPassword = providers.gradleProperty("pathlyReleaseKeyPassword").get()
      }
    }
  }

  buildTypes {
    // 開発版は別アプリ（com.pathly.debug）として入れ、普段使いのリリース版とデータを分ける。
    // 名前は src/debug/res の app_name で「開発版」と見分ける。
    debug {
      applicationIdSuffix = ".debug"
    }
    release {
      signingConfig = signingConfigs.findByName("release")
      // 鍵を替えるときのデータ移行専用（docs/adr/0026-release-versioning-and-signing.md の付録）。run-as で書き戻せるよう一時的に
      // debuggable にする。普段のリリースでは渡さない。
      isDebuggable = providers.gradleProperty("pathlyDebuggableRelease").orNull == "true"
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
  implementation(libs.hilt.lifecycle.viewmodel.compose)
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
  // BOM で core/json を同じ版に揃える（androidTest 側も追従する）。BOM は room-testing が
  // 引く版以上であれば上げてよい（揃っていることが要点）。
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
    ktlint("1.8.0").editorConfigOverride(
      mapOf(
        "indent_size" to "2",
        // @Composable関数はPascalCaseが慣例のため命名規則の対象外にする
        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
      ),
    )
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktlint("1.8.0").editorConfigOverride(
      mapOf(
        "indent_size" to "2",
      ),
    )
  }
}
