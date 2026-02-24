import 'dart:io';
import 'package:ffmpeg_kit_flutter_new_min_gpl/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new_min_gpl/return_code.dart';
import 'package:ffmpeg_kit_flutter_new_min_gpl/ffprobe_kit.dart';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:lofiga/logic/preset_manager.dart';
import 'dart:math' as math;

class ExportService {

  static Future<String?> exportTrack({
    required String inputPath,
    required PresetManager preset,
    required Function(double) onProgress,
  }) async {
    bool success = false;
    try {
      Directory? downloadsDir;
      if (Platform.isAndroid) {
        downloadsDir = await getExternalStorageDirectory();
      } else {
        downloadsDir = await getApplicationDocumentsDirectory();
      }

      final String outputPath = '${downloadsDir!.path}/lofiga_export_${DateTime.now().millisecondsSinceEpoch}.m4a';

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

      // Use native AAC encoder which is ALWAYS available in FFmpeg, avoiding missing libmp3lame errors
      args.addAll(['-c:a', 'aac', '-b:a', '256k', outputPath]);

      debugPrint('FFmpeg Command Args: ${args.join(' ')}');

      final session = await FFmpegKit.executeWithArguments(args);
      final returnCode = await session.getReturnCode();

      if (ReturnCode.isSuccess(returnCode)) {
         debugPrint('Export success');
         return outputPath;
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
