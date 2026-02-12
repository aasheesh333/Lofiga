import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:lofiga/logic/preset_manager.dart';
import 'package:lofiga/logic/audio_engine.dart';
import 'package:lofiga/logic/export_service.dart';
import 'dart:ui';
import 'dart:math' as math;

class PlayerEditorScreen extends StatefulWidget {
  final String filePath;
  final String fileName;

  const PlayerEditorScreen({
    super.key,
    required this.filePath,
    required this.fileName,
  });

  @override
  State<PlayerEditorScreen> createState() => _PlayerEditorScreenState();
}

class _PlayerEditorScreenState extends State<PlayerEditorScreen> with SingleTickerProviderStateMixin {
  late AudioEngine _engine;
  bool _isExporting = false;
  late AnimationController _waveController;

  @override
  void initState() {
    super.initState();
    _engine = AudioEngine();

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _engine.init();
      // Handle edge cases: verify file exists or is valid before loading
      // (Basic check done by AudioEngine try/catch, but UI feedback is good)
      await _engine.loadTrack(widget.filePath);
      if (mounted) {
        context.read<PresetManager>().applyPreset(LofiPreset.normal);
      }
    });

    _waveController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat();
  }

  @override
  void dispose() {
    _waveController.dispose();
    _engine.stop();
    super.dispose();
  }

  Future<void> _handleExport() async {
    setState(() {
      _isExporting = true;
    });

    final preset = context.read<PresetManager>();
    final path = await ExportService.exportTrack(
      inputPath: widget.filePath,
      preset: preset,
      onProgress: (p) {},
    );

    if (mounted) {
      setState(() => _isExporting = false);
      if (path != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Exported to: $path'),
            backgroundColor: Theme.of(context).primaryColor,
          ),
        );
      } else {
        // Could be cancelled or failed
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Export Cancelled or Failed')),
        );
      }
    }
  }

  Future<void> _cancelExport() async {
    await ExportService.cancelExport();
    if (mounted) {
      setState(() => _isExporting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF191022),
      body: Stack(
        children: [
          // Background Ambience
          Positioned(
            top: -100,
            left: -50,
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: const Color(0xFF993DF5).withOpacity(0.15),
                backgroundBlendMode: BlendMode.screen,
              ),
            ),
          ),
          Positioned(
            bottom: -100,
            right: -50,
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: const Color(0xFF3DF5E6).withOpacity(0.1),
                backgroundBlendMode: BlendMode.screen,
              ),
            ),
          ),
          BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 60, sigmaY: 60),
            child: Container(color: Colors.transparent),
          ),

          SafeArea(
            child: Column(
              children: [
                _buildTopSection(),
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                    child: Column(
                      children: [
                        _buildDSPControls(),
                        const SizedBox(height: 32),
                        _buildAtmosphereSection(),
                        const SizedBox(height: 100),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),

          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: _buildPresetPanel(),
          ),

          if (_isExporting) _buildExportOverlay(),
        ],
      ),
    );
  }

  Widget _buildTopSection() {
    return Padding(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              IconButton(
                icon: const Icon(Icons.keyboard_arrow_down, color: Colors.white),
                onPressed: () => Navigator.pop(context),
              ),
              Text(
                'NOW PLAYING',
                style: GoogleFonts.splineSans(
                  fontSize: 10,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 2,
                  color: Colors.white54,
                ),
              ),
              IconButton(
                icon: const Icon(Icons.ios_share, color: Colors.white),
                onPressed: _handleExport,
              ),
            ],
          ),
          const SizedBox(height: 24),
          StreamBuilder<bool>(
            stream: _engine.isPlayingStream,
            initialData: false,
            builder: (context, snapshot) {
              final isPlaying = snapshot.data ?? false;
              return AnimatedContainer(
                duration: const Duration(milliseconds: 500),
                width: isPlaying ? 240 : 220,
                height: isPlaying ? 240 : 220,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: const LinearGradient(
                    colors: [Color(0xFF581C87), Color(0xFF1E3A8A)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFF993DF5).withOpacity(isPlaying ? 0.4 : 0.1),
                      blurRadius: isPlaying ? 30 : 10,
                      spreadRadius: isPlaying ? 5 : 0,
                    ),
                  ],
                ),
                child: const Icon(Icons.music_note, size: 80, color: Colors.white24),
              );
            },
          ),
          const SizedBox(height: 24),
          Text(
            widget.fileName,
            textAlign: TextAlign.center,
            style: GoogleFonts.splineSans(
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 24),
          SizedBox(
            height: 40,
            child: AnimatedBuilder(
              animation: _waveController,
              builder: (context, child) {
                return CustomPaint(
                  painter: WaveformPainter(
                    color: Theme.of(context).primaryColor,
                    animationValue: _waveController.value,
                  ),
                  size: const Size(double.infinity, 40),
                );
              },
            ),
          ),
          const SizedBox(height: 16),
          StreamBuilder<bool>(
            stream: _engine.isPlayingStream,
            initialData: false,
            builder: (context, snapshot) {
              final isPlaying = snapshot.data ?? false;
              return Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  IconButton(
                    iconSize: 48,
                    icon: Icon(
                      isPlaying ? Icons.pause_circle_filled : Icons.play_circle_filled,
                      color: Colors.white,
                    ),
                    onPressed: _engine.togglePlayPause,
                  ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  Widget _buildDSPControls() {
    return Consumer<PresetManager>(
      builder: (context, preset, _) {
        return Column(
          children: [
            _buildSliderRow('TEMPO', '${(preset.tempo * 100).toInt()}%', preset.tempo, 0.75, 1.05, (v) {
              HapticFeedback.selectionClick();
              preset.setTempo(v);
            }),
            _buildSliderRow('PITCH', '${preset.pitch.toStringAsFixed(1)} st', preset.pitch, -4.0, 2.0, (v) {
               HapticFeedback.selectionClick();
               preset.setPitch(v);
            }),
            _buildSliderRow('REVERB', '${(preset.reverb * 100).toInt()}%', preset.reverb, 0.0, 0.60, (v) {
               HapticFeedback.selectionClick();
               preset.setReverb(v);
            }),
            _buildSliderRow('DELAY', '${(preset.delay * 100).toInt()}%', preset.delay, 0.0, 0.40, (v) {
               HapticFeedback.selectionClick();
               preset.setDelay(v);
            }),
            _buildSliderRow('BASS', '+${(preset.bass * 100).toInt()}%', preset.bass, 0.0, 0.40, (v) {
               HapticFeedback.selectionClick();
               preset.setBass(v);
            }),
            _buildSliderRow('CUT', '${(preset.trebleCut * 100).toInt()}%', preset.trebleCut, 0.0, 0.70, (v) {
               HapticFeedback.selectionClick();
               preset.setTrebleCut(v);
            }),
          ],
        );
      },
    );
  }

  Widget _buildSliderRow(String label, String valueLabel, double value, double min, double max, Function(double) onChanged) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16.0),
      child: Row(
        children: [
          SizedBox(
            width: 60,
            child: Text(
              label,
              style: GoogleFonts.splineSans(fontSize: 10, fontWeight: FontWeight.bold, color: Colors.white54, letterSpacing: 1),
            ),
          ),
          Expanded(
            child: SliderTheme(
              data: SliderTheme.of(context).copyWith(
                activeTrackColor: Theme.of(context).primaryColor,
                inactiveTrackColor: Colors.white10,
                thumbColor: Colors.white,
                trackHeight: 4,
                overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
              ),
              child: Slider(
                min: min,
                max: max,
                value: value,
                onChanged: onChanged,
              ),
            ),
          ),
          SizedBox(
            width: 40,
            child: Text(
              valueLabel,
              textAlign: TextAlign.right,
              style: GoogleFonts.splineSans(fontSize: 10, fontWeight: FontWeight.bold, color: Theme.of(context).colorScheme.secondary),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAtmosphereSection() {
    return Consumer<PresetManager>(
      builder: (context, preset, _) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'ATMOSPHERE LAYERS',
              style: GoogleFonts.splineSans(fontSize: 10, fontWeight: FontWeight.bold, color: Colors.white54, letterSpacing: 1),
            ),
            const SizedBox(height: 16),
            _buildAtmosphereRow('Rain', preset.rainVolume, (v) => preset.setAtmosphere('rain', v)),
            _buildAtmosphereRow('Vinyl', preset.vinylVolume, (v) => preset.setAtmosphere('vinyl', v)),
            _buildAtmosphereRow('Wind', preset.windVolume, (v) => preset.setAtmosphere('wind', v)),
            _buildAtmosphereRow('Tape', preset.tapeVolume, (v) => preset.setAtmosphere('tape', v)),
          ],
        );
      },
    );
  }

  Widget _buildAtmosphereRow(String label, double volume, Function(double) onChanged) {
    bool isActive = volume > 0;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12.0),
      child: Row(
        children: [
          Icon(
            isActive ? Icons.volume_up : Icons.volume_off,
            size: 16,
            color: isActive ? Colors.white : Colors.white24,
          ),
          const SizedBox(width: 12),
          Text(label, style: GoogleFonts.splineSans(color: isActive ? Colors.white : Colors.white38, fontSize: 12)),
          Expanded(
            child: SliderTheme(
              data: SliderTheme.of(context).copyWith(
                activeTrackColor: isActive ? Colors.white : Colors.white10,
                inactiveTrackColor: Colors.white10,
                thumbColor: isActive ? Colors.white : Colors.white38,
                trackHeight: 2,
                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 4),
              ),
              child: Slider(
                value: volume,
                onChanged: (v) {
                  HapticFeedback.selectionClick();
                  onChanged(v);
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPresetPanel() {
    return Container(
      height: 100,
      decoration: BoxDecoration(
        color: const Color(0xFF231B2E).withOpacity(0.95),
        border: const Border(top: BorderSide(color: Colors.white10)),
        boxShadow: [
           BoxShadow(color: Colors.black.withOpacity(0.5), blurRadius: 20, offset: const Offset(0, -5)),
        ],
      ),
      child: ClipRRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
            children: [
              _buildPresetChip('Normal', LofiPreset.normal),
              _buildPresetChip('Lofi Slow', LofiPreset.lofiSlow),
              _buildPresetChip('Rainy Night', LofiPreset.rainyNight),
              _buildPresetChip('Vintage', LofiPreset.vintage),
              _buildPresetChip('Dreamy', LofiPreset.dreamy),
              _buildPresetChip('Sad', LofiPreset.sad),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPresetChip(String label, LofiPreset presetValue) {
    return Consumer<PresetManager>(
      builder: (context, manager, _) {
        final isSelected = manager.currentPreset == presetValue;
        return GestureDetector(
          onTap: () {
            HapticFeedback.mediumImpact();
            manager.applyPreset(presetValue);
          },
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            margin: const EdgeInsets.only(right: 12),
            padding: const EdgeInsets.symmetric(horizontal: 20),
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: isSelected ? Theme.of(context).primaryColor : Colors.white.withOpacity(0.05),
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: isSelected ? Theme.of(context).primaryColor : Colors.white10,
                width: isSelected ? 2 : 1,
              ),
              boxShadow: isSelected ? [
                BoxShadow(color: Theme.of(context).primaryColor.withOpacity(0.4), blurRadius: 10),
              ] : [],
            ),
            child: Text(
              label,
              style: GoogleFonts.splineSans(
                fontWeight: FontWeight.bold,
                fontSize: 12,
                color: isSelected ? Colors.white : Colors.white60,
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildExportOverlay() {
    return Container(
      color: Colors.black87,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(color: Color(0xFF993DF5)),
            const SizedBox(height: 24),
            Text(
              'Rendering Lofi Mix...',
              style: GoogleFonts.splineSans(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'This happens offline on your device.',
              style: GoogleFonts.splineSans(color: Colors.white54, fontSize: 12),
            ),
            const SizedBox(height: 32),
            OutlinedButton(
              onPressed: _cancelExport,
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Colors.white30),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
              ),
              child: Text('Cancel', style: GoogleFonts.splineSans(color: Colors.white70)),
            ),
          ],
        ),
      ),
    );
  }
}

class WaveformPainter extends CustomPainter {
  final Color color;
  final double animationValue;
  WaveformPainter({required this.color, required this.animationValue});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color.withOpacity(0.5)
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final path = Path();
    final width = size.width;
    final height = size.height;

    path.moveTo(0, height * 0.5);
    for (double i = 0; i < width; i += 5) {
      double wave = math.sin((i / 20) + (animationValue * 2 * math.pi));
      path.lineTo(i, height * 0.5 + (wave * 10));
    }

    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant WaveformPainter oldDelegate) =>
      oldDelegate.animationValue != animationValue;
}
