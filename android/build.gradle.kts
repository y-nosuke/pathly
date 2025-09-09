// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.kotlin.kapt) apply false
  id("com.diffplug.spotless") version "7.2.1" apply false
}