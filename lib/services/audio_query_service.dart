import 'dart:io';
import 'package:flutter/services.dart';

/// Unified audio query service.
///
/// Both platforms use a native MethodChannel:
/// - **Android**: MainActivity.kt queries MediaStore directly (no extra native libs).
/// - **iOS**: Native Swift code uses MPMediaQuery from the MediaPlayer framework.
class AudioQueryService {
  static const MethodChannel _channel =
      MethodChannel('com.example.lofiga/audio_query');

  Future<bool> permissionsStatus() async {
    if (Platform.isIOS) {
      try {
        final result = await _channel.invokeMethod<bool>('permissionsStatus');
        return result ?? false;
      } on PlatformException {
        return false;
      }
    }
    return true;
  }

  Future<bool> permissionsRequest() async {
    if (Platform.isIOS) {
      try {
        final result = await _channel.invokeMethod<bool>('permissionsRequest');
        return result ?? false;
      } on PlatformException {
        return false;
      }
    }
    return true;
  }

  Future<List<Map<String, dynamic>>> querySongs() async {
    try {
      final List<dynamic> result =
          await _channel.invokeMethod('querySongs');
      return result
          .map((item) => Map<String, dynamic>.from(item as Map))
          .toList();
    } on PlatformException catch (e) {
      print("Failed to query songs natively: '${e.message}'.");
      return [];
    }
  }
}
