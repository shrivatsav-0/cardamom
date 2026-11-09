plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.shrivatsav.cardamom.vision"
  compileSdk = 37
  defaultConfig {
    minSdk = 30
  }
  buildFeatures {
    compose = true
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
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  implementation(libs.androidx.compose.ui)
  implementation(project(":core:model"))
  implementation(project(":core:util"))
  implementation(project(":core:ui"))

  implementation("com.google.mlkit:barcode-scanning:17.3.0")
  implementation("com.google.mlkit:text-recognition:16.0.1")
  implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
  implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
  implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
  implementation("com.google.mlkit:text-recognition-korean:16.0.1")
  // NOTE: onnxruntime-android removed — no code references ai.onnxruntime.*
  // (PaddleOcrEngine delegates to ML Kit). It was ~140 MB (universal APK)
  // / ~34 MB per ABI of dead weight. Re-add only if a real ONNX session is implemented.

  implementation(libs.androidx.core.ktx)

  testImplementation(libs.junit)
  testImplementation("org.json:json:20240303")
}
