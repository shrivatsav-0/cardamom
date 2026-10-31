# Cardamom Models & JSON Serialization
-keepclassmembers class com.shrivatsav.cardamom.WalletCard { *; }
-keepclassmembers class com.shrivatsav.cardamom.WalletDocument { *; }
-keepclassmembers class com.shrivatsav.cardamom.CardField { *; }
-keepclassmembers class com.shrivatsav.cardamom.CodeFormat { *; }
-keepclassmembers class com.shrivatsav.cardamom.LogoMode { *; }
-keepclassmembers class com.shrivatsav.cardamom.AppTheme { *; }
-keepclassmembers class com.shrivatsav.cardamom.LockTimeout { *; }

# Google ML Kit Vision & Barcode Scanning
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# ZXing Core
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Biometric & Fragment
-keep class androidx.biometric.** { *; }

# Kotlin Coroutines & Flow
-keepclassmembers class kotlinx.coroutines.** { *; }

# Widgets
-keep class com.shrivatsav.cardamom.CardWidgetProvider { *; }
-keep class com.shrivatsav.cardamom.CardWidgetConfigureActivity { *; }
