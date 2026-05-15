# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# DataStore
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences { *; }

# Compose
-dontwarn androidx.compose.**

# Audio effects (android.media.audiofx)
-keep class android.media.audiofx.** { *; }
-dontwarn android.media.audiofx.**

# MediaCodec / MediaExtractor / MediaMuxer
-keep class android.media.MediaCodec { *; }
-keep class android.media.MediaExtractor { *; }
-keep class android.media.MediaMuxer { *; }
-keep class android.media.MediaFormat { *; }

# Keep model classes for serialization
-keep class com.dhanuk.lofiga.model.** { *; }
-keep class com.dhanuk.lofiga.data.** { *; }

# ExportService reflection-safe
-keep class com.dhanuk.lofiga.export.** { *; }

# AdMob
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }
-dontwarn com.google.android.gms.ads.**

# User Messaging Platform (Consent)
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**
