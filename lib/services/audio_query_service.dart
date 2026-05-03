import 'dart:io';
import 'package:flutter/services.dart';
import 'package:on_audio_query/on_audio_query.dart';

/// Unified audio query service.
///
/// **Android**: Uses a custom native MethodChannel (in MainActivity.kt)
/// to query MediaStore directly, avoiding all third-party native libs
/// that may crash on older Android versions.
///
/// **iOS**: Uses the on_audio_query package (Apple-only, no native lib risk).
class AudioQueryService {
  OnAudioQuery? _iosAudioQuery;
  static const MethodChannel _androidChannel =
      MethodChannel('com.example.lofiga/audio_query');

  /// Lazily-created iOS query instance.
  /// Not created on Android — prevents any JNI native library loading.
  OnAudioQuery get _iosQuery => _iosAudioQuery ??= OnAudioQuery();

  Future<bool> permissionsStatus() async {
    if (Platform.isIOS) {
      return await _iosQuery.permissionsStatus();
    }
    return true;
  }

  Future<bool> permissionsRequest() async {
    if (Platform.isIOS) {
      return await _iosQuery.permissionsRequest();
    }
    return true;
  }

  Future<List<Map<String, dynamic>>> querySongs() async {
    if (Platform.isIOS) {
      return (await _iosQuery.querySongs(
        sortType: SongSortType.DATE_ADDED,
        orderType: OrderType.DESC_OR_GREATER,
        uriType: UriType.EXTERNAL,
        ignoreCase: true,
      ))
          .map((s) => <String, dynamic>{
                'id': s.id,
                'title': s.title,
                'artist': s.artist,
                'data': s.data,
                'duration': s.duration,
                'date_added': s.dateAdded,
              })
          .toList();
    } else if (Platform.isAndroid) {
      // Custom native channel – no on_audio_query / libdartjni.so.
      try {
        final List<dynamic> result =
            await _androidChannel.invokeMethod('querySongs');
        return result
            .map((item) => Map<String, dynamic>.from(item as Map))
            .toList();
      } on PlatformException catch (e) {
        print("Failed to query songs natively: '${e.message}'.");
        return [];
      }
    }
    return [];
  }
}
