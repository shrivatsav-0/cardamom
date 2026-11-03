plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.shrivatsav.cardamom.model"
  compileSdk = 37
  defaultConfig {
    minSdk = 30
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation("com.google.zxing:core:3.5.3")
  implementation("com.google.mlkit:barcode-scanning:17.3.0")
  testImplementation(libs.junit)
  testImplementation("org.json:json:20240303")
}
