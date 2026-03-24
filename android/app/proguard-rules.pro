# Flutter Wrapper
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# Plugins
-keep class miguelruivo.flutter.plugins.filepicker.** { *; }
-keep class com.baseflow.permissionhandler.** { *; }
-keep class com.lucasjosino.on_audio_query.** { *; }

-dontwarn miguelruivo.flutter.plugins.filepicker.**
-dontwarn com.baseflow.permissionhandler.**
-dontwarn com.lucasjosino.on_audio_query.**
