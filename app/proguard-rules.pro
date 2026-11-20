# ProGuard Rules for Downloadivo (Enterprise Production)

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# Jetpack Compose
-keepclassmembers class * extends androidx.compose.ui.Modifier$Element { *; }

# Media3 ExoPlayer
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-dontwarn androidx.media3.**

# OkHttp & Retrofit (if used)
-dontwarn okhttp3.**
-dontwarn okio.**

# Downloadivo Data Models
-keep class com.raoprime.downloadivo.data.** { *; }
