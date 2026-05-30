import 'dart:io';
import 'package:ffmpeg_kit_flutter_new_min_gpl/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new_min_gpl/return_code.dart';
import 'package:ffmpeg_kit_flutter_new_min_gpl/ffprobe_kit.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:lofiga/logic/preset_manager.dart';
import 'package:lofiga/services/storage_service.dart';
import 'dart:math' as math;

class ExportService {

  static Future<String?> exportTrack({
    required String inputPath,
    required String fileName,
    required PresetManager preset,
    required Function(double) onProgress,
  }) async {
    bool success = false;
    try {
      final settings = await StorageService().loadAppSettings();
      String ext = settings.audioFormat;
      String bitrate = settings.audioBitrate;

      String basePath;
      if (settings.exportPath.isNotEmpty) {
        basePath = settings.exportPath;
      } else {
        Directory? downloadsDir;
        if (Platform.isAndroid) {
          downloadsDir = await getExternalStorageDirectory();
        } else {
          downloadsDir = await getApplicationDocumentsDirectory();
        }
        basePath = downloadsDir!.path;
      }

      // Extract base name without extension
      String cleanName = fileName;
      int dotIndex = cleanName.lastIndexOf('.');
      if (dotIndex != -1) {
        cleanName = cleanName.substring(0, dotIndex);
      }
      // Clean up name to be safe for filesystems
      cleanName = cleanName.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');

      // Get preset string
      String presetStr = preset.customPresetName ?? preset.currentPreset.toString().split('.').last;

      final String finalOutputPath = '$basePath/${cleanName} - ${presetStr} - ${bitrate}.$ext';
      
      // On Android 11+, FFmpeg cannot write directly to external directories
      // We must write to the app's temporary directory first, then copy with Flutter File
      Directory tempDir = await getTemporaryDirectory();
      final String tempOutputPath = '${tempDir.path}/temp_export_${DateTime.now().millisecondsSinceEpoch}.$ext';

      // 1. Get Sample Rate safely
      int sampleRate = 44100;
      try {
        final session = await FFprobeKit.getMediaInformation(inputPath);
        final info = session.getMediaInformation();
        if (info != null && info.getStreams().isNotEmpty) {
           final rate = info.getStreams().first.getSampleRate();
           if (rate != null) {
             sampleRate = int.tryParse(rate) ?? 44100;
           }
        }
      } catch (e) {
        debugPrint('FFprobe failed, defaulting to 44100: $e');
      }

      // Build FFmpeg Filter Complex safely
      List<String> filters = [];

      double pitchFactor = math.pow(2, preset.pitch / 12.0).toDouble();
      double tempo = preset.tempo;
      double combinedFactor = tempo * pitchFactor;

      if (combinedFactor != 1.0) {
        int newRate = (sampleRate * combinedFactor).toInt();
        filters.add('asetrate=$newRate');
        filters.add('aresample=44100'); // Force output to 44.1kHz to prevent encoder crashes
      }

      if (preset.trebleCut > 0) {
        // limit freq to avoid invalid lowpass values
        double cutPct = preset.trebleCut.clamp(0.0, 1.0);
        double freq = 20000 - (cutPct * 18000);
        if (freq < 200) freq = 200;
        filters.add('lowpass=f=${freq.toInt()}');
      }

      if (preset.bass > 0) {
        // equalizer filter is not in min-gpl. Using bass filter instead.
        double gain = (preset.bass * 20).clamp(0.0, 20.0);
        filters.add('bass=g=$gain:f=100:w=0.5');
      }

      if (preset.delay > 0) {
        int delayMs = (preset.delay * 1000).toInt().clamp(10, 2000);
        filters.add('aecho=0.8:0.6:$delayMs:0.3');
      }

      if (preset.reverb > 0) {
        // freeverb filter is not in min-gpl. Simulating reverb with multi-tap echo.
        // aecho=in_gain:out_gain:delays:decays
        double rev = preset.reverb.clamp(0.0, 1.0);
        int d1 = (40 + (rev * 40)).toInt(); // 40-80ms
        int d2 = (90 + (rev * 60)).toInt(); // 90-150ms
        filters.add('aecho=0.8:0.88:$d1|$d2:0.4|0.3');
      }

      // Ensure a 44.1kHz stereo format for compatibility
      filters.add('aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo');

      // Construct the command arguments safely
      List<String> args = ['-y', '-i', inputPath];

      if (filters.isNotEmpty) {
        String filterGraph = filters.join(',');
        args.addAll(['-af', filterGraph]); 
      }

      if (ext == 'wav') {
         args.addAll(['-c:a', 'pcm_s16le', tempOutputPath]);
      } else if (ext == 'mp3') {
         args.addAll(['-c:a', 'libmp3lame', '-b:a', bitrate, tempOutputPath]);
      } else {
         // Default to aac
         args.addAll(['-c:a', 'aac', '-b:a', bitrate, tempOutputPath]);
      }

      debugPrint('FFmpeg Command Args: ${args.join(' ')}');

      final session = await FFmpegKit.executeWithArguments(args);
      final returnCode = await session.getReturnCode();

      if (ReturnCode.isSuccess(returnCode)) {
         debugPrint('FFmpeg Export success to temp directory. Copying to final destination...');

         // Safely copy the file from temp to the requested export path.
         // If the custom path is not writable (e.g. a folder outside the app
         // sandbox on Android 11+), fall back to the app's own directory so the
         // user never loses their rendered track.
         final tempFile = File(tempOutputPath);
         try {
           if (await tempFile.exists()) {
             await tempFile.copy(finalOutputPath);
             await tempFile.delete();
           }
           return finalOutputPath;
         } catch (copyError) {
           debugPrint('Primary save to $finalOutputPath failed: $copyError. Falling back to app directory.');
           try {
             final Directory fallbackDir = Platform.isAndroid
                 ? (await getExternalStorageDirectory() ?? await getApplicationDocumentsDirectory())
                 : await getApplicationDocumentsDirectory();
             final String fallbackPath =
                 '${fallbackDir.path}/${cleanName} - ${presetStr} - ${bitrate}.$ext';
             if (await tempFile.exists()) {
               await tempFile.copy(fallbackPath);
               await tempFile.delete();
             }
             return fallbackPath;
           } catch (fallbackError) {
             throw Exception(
                 "FFmpeg succeeded but the file could not be saved: $fallbackError");
           }
         }
      } else if (ReturnCode.isCancel(returnCode)) {
         debugPrint('Export cancelled by user');
         throw Exception("ExportCancelled");
      } else {
         final logs = await session.getLogs();
         List<String> logMsgs = logs.map((l) => l.getMessage().trim()).where((s) => s.isNotEmpty).toList();
         if (logMsgs.length > 15) {
           logMsgs = logMsgs.sublist(logMsgs.length - 15);
         }
         String logString = logMsgs.join('\n');
         throw Exception("FFmpeg Error ($returnCode):\n$logString");
      }

    } catch (e) {
      debugPrint('Export Error: $e');
      throw Exception(e.toString());
    }
  }

  static Future<void> cancelExport() async {
    await FFmpegKit.cancel();
  }
}
