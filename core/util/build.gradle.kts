plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.shrivatsav.cardamom.util"
  compileSdk = 37
  defaultConfig {
    minSdk = 30
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  testOptions {
    unitTests.isReturnDefaultValues = true
  }
}

kotlin {
  jvmToolchain(17)
}
dependencies {
  implementation(project(":core:model"))
  implementation("com.google.zxing:core:3.5.3")
  implementation("com.google.android.gms:play-services-tasks:18.2.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("androidx.biometric:biometric:1.1.0")
  implementation("androidx.fragment:fragment-ktx:1.8.5")
  implementation("androidx.security:security-crypto:1.1.0")
  implementation(libs.androidx.core.ktx)
  testImplementation(libs.junit)
}
