# Flutter Wrapper
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# Plugins
-keep class * implements io.flutter.plugin.common.PluginRegistry$PluginRegistrantCallback { *; }
-keep class * implements io.flutter.plugin.common.MethodChannel$MethodCallHandler { *; }

# Prevents JNI class lookup crashes on older Android versions (e.g. Android 9 MIUI).
# Native libraries (flutter_soloud, ffmpeg_kit, just_audio) may call FindClass
# through JNI, which requires their Java counterparts to be kept.
-keep class com.dhanuk.lofiga.** { *; }

-dontwarn io.flutter.**

# FFmpeg Kit - keep JNI callback classes
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.arthenica.smartexception.** { *; }

# flutter_soloud - keep JNI classes
-keep class com.alexmercerind.flutter_soloud.** { *; }

# just_audio / audio_session - keep plugin classes
-keep class com.ryanheise.audiosession.** { *; }
