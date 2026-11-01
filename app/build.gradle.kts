import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val keystoreProperties = Properties().apply {
  val keystorePropertiesFile = project.file("keystore.properties")
  if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
  namespace = "com.shrivatsav.cardamom"
  compileSdk = 37
  defaultConfig {
    applicationId = "com.shrivatsav.cardamom"
    minSdk = 30
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
  }
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64")
      isUniversalApk = false
    }
  }

  signingConfigs {
    create("release") {
      val storeFilePath = keystoreProperties.getProperty("storeFile") ?: "cardamom-release.jks"
      storeFile = project.file(storeFilePath)
      storePassword = keystoreProperties.getProperty("storePassword") ?: ""
      keyAlias = keystoreProperties.getProperty("keyAlias") ?: "cardamom"
      keyPassword = keystoreProperties.getProperty("keyPassword") ?: ""
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation("org.json:json:20240303")

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  implementation("androidx.fragment:fragment-ktx:1.8.5")
  implementation("androidx.biometric:biometric:1.1.0")

  implementation("androidx.camera:camera-core:1.4.2")
  implementation("androidx.camera:camera-camera2:1.4.2")
  implementation("androidx.camera:camera-lifecycle:1.4.2")
  implementation("androidx.camera:camera-view:1.4.2")

  implementation("com.google.mlkit:barcode-scanning:17.3.0")
  implementation("com.google.mlkit:text-recognition:16.0.1")
  implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
  implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
  implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
  implementation("com.google.mlkit:text-recognition-korean:16.0.1")
  implementation("com.google.zxing:core:3.5.3")
  implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")

  implementation("androidx.navigation:navigation-compose:2.8.9")
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.material:material-icons-extended")

}
