import 'dart:io';
import 'package:flutter/services.dart';
import 'package:on_audio_query/on_audio_query.dart';

class AudioQueryService {
  final OnAudioQuery _iosAudioQuery = OnAudioQuery();
  static const MethodChannel _androidChannel = MethodChannel('com.example.lofiga/audio_query');

  Future<bool> permissionsStatus() async {
    if (Platform.isIOS) {
      return await _iosAudioQuery.permissionsStatus();
    }
    // Handled purely by permission_handler in HomeScreen for Android.
    return true;
  }

  Future<bool> permissionsRequest() async {
    if (Platform.isIOS) {
      return await _iosAudioQuery.permissionsRequest();
    }
    return true;
  }

  Future<List<SongModel>> querySongs() async {
    if (Platform.isIOS) {
      // Use the existing package for iOS so we don't break anything.
      return await _iosAudioQuery.querySongs(
        sortType: SongSortType.DATE_ADDED,
        orderType: OrderType.DESC_OR_GREATER,
        uriType: UriType.EXTERNAL,
        ignoreCase: true,
      );
    } else if (Platform.isAndroid) {
      // Use custom MethodChannel for Android to avoid native crash on Android 9
      try {
        final List<dynamic> result = await _androidChannel.invokeMethod('querySongs');
        List<SongModel> songs = [];
        for (var item in result) {
          final map = Map<String, dynamic>.from(item as Map);

          // Map to SongModel expected properties
          final mapped = {
            "_id": map["id"],
            "title": map["title"],
            "artist": map["artist"],
            "_data": map["data"],
            "duration": map["duration"],
            "date_added": map["date_added"],
          };

          // Use SongModel internal constructor via parsing if possible, or construct dummy map.
          // Since SongModel expects a specific map, we format it as such.
          songs.add(SongModel(mapped));
        }
        return songs;
      } on PlatformException catch (e) {
        print("Failed to query songs natively: '\${e.message}'.");
        return [];
      }
    }
    return [];
  }
}
