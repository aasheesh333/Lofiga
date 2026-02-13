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

      final String outputPath = '${downloadsDir!.path}/lofiga_export_${DateTime.now().millisecondsSinceEpoch}.mp3';

      // 1. Get Sample Rate
      int sampleRate = 44100;
      await FFprobeKit.getMediaInformation(inputPath).then((session) async {
         final info = session.getMediaInformation();
         if (info != null && info.getStreams().isNotEmpty) {
            final rate = info.getStreams().first.getSampleRate();
            if (rate != null) {
              sampleRate = int.tryParse(rate) ?? 44100;
            }
         }
      });

      // Build FFmpeg Filter Complex
      List<String> filters = [];

      double pitchFactor = math.pow(2, preset.pitch / 12.0).toDouble();
      double tempo = preset.tempo;
      double combinedFactor = tempo * pitchFactor;

      if (combinedFactor != 1.0) {
        int newRate = (sampleRate * combinedFactor).toInt();
        filters.add('asetrate=$newRate');
        filters.add('aresample=$sampleRate');
      }

      if (preset.trebleCut > 0) {
        double freq = 20000 - (preset.trebleCut * 18000);
        filters.add('lowpass=f=$freq');
      }

      if (preset.bass > 0) {
        double gain = preset.bass * 20;
        filters.add('equalizer=f=100:width_type=h:width=200:g=$gain');
      }

      if (preset.delay > 0) {
        int delayMs = (preset.delay * 1000).toInt();
        if (delayMs < 10) delayMs = 10;
        filters.add('aecho=0.8:0.6:$delayMs:0.3');
      }

      if (preset.reverb > 0) {
        filters.add('freeverb=width=0.9:wet=${preset.reverb}:damp=0.5:room=0.8');
      }

      filters.add('loudnorm=I=-16:TP=-1.5:LRA=11');

      String cmd = '-y -i "$inputPath" ';

      if (filters.isNotEmpty) {
        String filterGraph = filters.join(',');
        cmd += '-filter_complex "$filterGraph" ';
      }

      cmd += '-b:a 320k "$outputPath"';

      debugPrint('FFmpeg Command: $cmd');

      final session = await FFmpegKit.execute(cmd);
      final returnCode = await session.getReturnCode();

      if (ReturnCode.isSuccess(returnCode)) {
         debugPrint('Export success');
         success = true;
         return outputPath;
      } else if (ReturnCode.isCancel(returnCode)) {
         debugPrint('Export cancelled');
         return null;
      } else {
         debugPrint('Export failed with code: $returnCode');
         return null;
      }

    } catch (e) {
      debugPrint('Export Error: $e');
      return null;
    }
  }

  static Future<void> cancelExport() async {
    await FFmpegKit.cancel();
  }
}
