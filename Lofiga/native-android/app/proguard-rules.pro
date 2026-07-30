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

# App-owned manifest-registered components (Application subclass, MediaSessionService
# subclass, Activities). AAPT normally keeps manifest-referenced classes, but Media3
# also dispatches callbacks into the MediaSessionService subclass via reflection, so
# we keep the class + its public/onTaskRemoved overrides explicitly to be safe.
-keep class com.dhanuk.lofiga.LofigaApplication
-keep class com.dhanuk.lofiga.media.MediaPlaybackService { public *; }
-keep class com.dhanuk.lofiga.media.Media3MediaSessionManager { *; }

# Media3 1.5.x — library ships consumer rules, but Transformer touches some
# Effect/MediaItem subclasses by name; override to be safe.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# AdMob
-keep public class com.google.android.gms.ads.** { public *; }
-keep public class com.google.ads.** { public *; }
-dontwarn com.google.android.gms.ads.**

# User Messaging Platform (Consent)
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# OneSignal
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**

# Keep BuildConfig (used at runtime for ad/OneSignal IDs).
-keep class com.dhanuk.lofiga.BuildConfig { *; }

# Kotlin metadata — keep enum names for MoodTag + other koordinate-aware code.
-keepclassmembers enum com.dhanuk.lofiga.** {
    public **[] values();
    public ** valueOf(java.lang.String);
}
