package com.alexmercerind.on_audio_query

import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/**
 * No-op shadow plugin that replaces on_audio_query's real Android native plugin.
 *
 * The real on_audio_query v2.x depends on the `jni` Dart package, which ships
 * `libdartjni.so`. On Android 9 (especially Xiaomi MIUI), this native library
 * crashes with SIGSEGV in `FindClassUnchecked` during Flutter engine init,
 * because the ART classloader reference is null in that context.
 *
 * Since we never call on_audio_query from Dart on Android (we use a custom
 * MethodChannel in MainActivity instead), this shadow prevents the real
 * plugin from ever loading libdartjni.so. iOS is unaffected because it does
 * not use this AAR.
 */
class OnAudioQueryPlugin : FlutterPlugin, MethodCallHandler {

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        // Intentionally empty – no native libs loaded, no JNI calls.
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        // Never called from Dart on Android (Platform.isIOS guards in Dart).
        result.notImplemented()
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        // Intentionally empty.
    }

    companion object {
        @JvmStatic
        fun registerWith(registrar: io.flutter.plugin.common.PluginRegistry.Registrar) {
            // Legacy registration – intentionally empty.
        }
    }
}