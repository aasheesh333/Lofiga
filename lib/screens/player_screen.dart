import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';
import 'package:google_fonts/google_fonts.dart';
import 'dart:math' as math;
import 'package:lofiga/logic/audio_effects_manager.dart';
import 'package:lofiga/screens/export_screen.dart';
import 'dart:ui'; // For BackdropFilter

class PlayerScreen extends StatefulWidget {
  final String filePath;
  final String fileName;

  const PlayerScreen({
    super.key,
    required this.filePath,
    required this.fileName,
  });

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> with SingleTickerProviderStateMixin {
  late AudioPlayer _audioPlayer;
  late AudioEffectsManager _effectsManager;
  bool _isPlaying = false;
  Duration _duration = Duration.zero;
  Duration _position = Duration.zero;

  @override
  void initState() {
    super.initState();
    _audioPlayer = AudioPlayer();
    _effectsManager = AudioEffectsManager(_audioPlayer);
    _initAudio();
  }

  Future<void> _initAudio() async {
    try {
      await _audioPlayer.setFilePath(widget.filePath);
      _duration = _audioPlayer.duration ?? Duration.zero;
      
      _audioPlayer.playerStateStream.listen((state) {
        if (mounted) {
          setState(() {
            _isPlaying = state.playing;
          });
        }
      });

      _audioPlayer.positionStream.listen((position) {
        if (mounted) {
          setState(() {
            _position = position;
          });
        }
      });

      // Auto play
      _audioPlayer.play();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error playing audio: $e')),
        );
      }
    }
  }

  @override
  void dispose() {
    _audioPlayer.dispose();
    super.dispose();
  }

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');
    final minutes = twoDigits(duration.inMinutes.remainder(60));
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    return '$minutes:$seconds';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: Stack(
        children: [
          // Ambient Background Glows
          Positioned(
            top: -100,
            left: -50,
            child: Container(
              width: 350,
              height: 350,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Theme.of(context).primaryColor.withOpacity(0.15),
                backgroundBlendMode: BlendMode.screen,
              ),
            ),
          ),
          Positioned(
            bottom: 100,
            right: -100,
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Theme.of(context).colorScheme.secondary.withOpacity(0.1),
                backgroundBlendMode: BlendMode.screen,
              ),
            ),
          ),
          // Blur the glows
          BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 80, sigmaY: 80),
            child: Container(color: Colors.transparent),
          ),

          SafeArea(
            child: Column(
              children: [
                // Top Navigation
                Padding(
                  padding: const EdgeInsets.all(24.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                       Container(
                        width: 40, height: 40,
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.05),
                          shape: BoxShape.circle,
                        ),
                        child: IconButton(
                          icon: const Icon(Icons.keyboard_arrow_down, color: Colors.white),
                          onPressed: () => Navigator.pop(context),
                        ),
                      ),
                      Text(
                        'NOW PLAYING',
                        style: GoogleFonts.splineSans(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          letterSpacing: 2,
                          color: Colors.white70,
                        ),
                      ),
                       Container(
                        width: 40, height: 40,
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.05),
                          shape: BoxShape.circle,
                        ),
                        child: IconButton(
                          icon: const Icon(Icons.ios_share, color: Colors.white, size: 20),
                          onPressed: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(builder: (context) => ExportScreen(fileName: widget.fileName)),
                            );
                          },
                        ),
                      ),
                    ],
                  ),
                ),

                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      // Album Art Container with Glow
                      Stack(
                        alignment: Alignment.center,
                        children: [
                          // Glow behind
                          Container(
                            width: 280,
                            height: 280,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: Theme.of(context).primaryColor.withOpacity(0.4),
                            ),
                          ).animate().pulse(duration: const Duration(seconds: 3)), // Needs flutter_animate or custom animation, using simple container for now with manual blur in stack
                          
                          // Actual Art
                          Container(
                            width: 300,
                            height: 300,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(40),
                              boxShadow: [
                                BoxShadow(
                                  color: Theme.of(context).primaryColor.withOpacity(0.3),
                                  blurRadius: 30,
                                  spreadRadius: 0,
                                ),
                              ],
                              gradient: LinearGradient(
                                begin: Alignment.topLeft,
                                end: Alignment.bottomRight,
                                colors: [
                                  Colors.purple.shade800,
                                  Colors.blue.shade900,
                                ],
                              ),
                            ),
                            child: const Icon(Icons.music_note, size: 120, color: Colors.white24),
                          ),
                        ],
                      ),
                      
                      const SizedBox(height: 48),

                      // Track Info
                      Text(
                        widget.fileName,
                        textAlign: TextAlign.center,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: GoogleFonts.splineSans(
                          fontSize: 24,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: Colors.white12),
                        ),
                        child: Text(
                          'LOFI REMIX',
                          style: GoogleFonts.splineSans(
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                            letterSpacing: 1.5,
                            color: Colors.white70,
                          ),
                        ),
                      ),
                      
                      const SizedBox(height: 32),

                      // Waveform Visualizer (Static SVG representation for now using CustomPaint)
                      SizedBox(
                        height: 60,
                        width: double.infinity,
                        child: CustomPaint(
                          painter: WaveformPainter(color: Theme.of(context).primaryColor),
                        ),
                      ),
                    ],
                  ),
                ),

                // Controls Footer
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 40),
                  child: Column(
                    children: [
                      // Progress Bar
                      Column(
                        children: [
                          SliderTheme(
                            data: SliderTheme.of(context).copyWith(
                              activeTrackColor: Theme.of(context).colorScheme.secondary, // Cyan
                              inactiveTrackColor: Colors.white10,
                              thumbColor: Colors.white,
                              trackHeight: 4,
                              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
                              overlayShape: const RoundSliderOverlayShape(overlayRadius: 12),
                            ),
                            child: Slider(
                              min: 0,
                              max: _duration.inSeconds.toDouble(),
                              value: _position.inSeconds.toDouble().clamp(0, _duration.inSeconds.toDouble()),
                              onChanged: (value) async {
                                final position = Duration(seconds: value.toInt());
                                await _audioPlayer.seek(position);
                              },
                            ),
                          ),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 8),
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.spaceBetween,
                              children: [
                                Text(_formatDuration(_position), style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white38)),
                                Text(_formatDuration(_duration), style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white38)),
                              ],
                            ),
                          ),
                        ],
                      ),

                      const SizedBox(height: 32),

                      // Playback Controls
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          IconButton(
                            icon: const Icon(Icons.shuffle, color: Colors.white38),
                            onPressed: () {},
                          ),
                          Row(
                            children: [
                              IconButton(
                                icon: const Icon(Icons.skip_previous, color: Colors.white, size: 32),
                                onPressed: () {},
                              ),
                              const SizedBox(width: 24),
                              GestureDetector(
                                onTap: () {
                                  if (_isPlaying) {
                                    _audioPlayer.pause();
                                  } else {
                                    _audioPlayer.play();
                                  }
                                },
                                child: Container(
                                  width: 72,
                                  height: 72,
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: Theme.of(context).primaryColor,
                                    boxShadow: [
                                      BoxShadow(
                                        color: Theme.of(context).primaryColor.withOpacity(0.5),
                                        blurRadius: 20,
                                        spreadRadius: 2,
                                      ),
                                    ],
                                  ),
                                  child: Icon(
                                    _isPlaying ? Icons.pause : Icons.play_arrow,
                                    color: Colors.white,
                                    size: 32,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 24),
                              IconButton(
                                icon: const Icon(Icons.skip_next, color: Colors.white, size: 32),
                                onPressed: () {},
                              ),
                            ],
                          ),
                          IconButton(
                            icon: const Icon(Icons.repeat, color: Colors.white38),
                            onPressed: () {},
                          ),
                        ],
                      ),
                      
                      const SizedBox(height: 16),
                      
                      // Effects Chip (Navigates to Editor or just toggles preset)
                      // For now, let's keep it simple as a preset toggle or indicator
                      GestureDetector(
                        onTap: () {
                           // Cycle presets logic could go here or navigate to Editor
                        },
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                          decoration: BoxDecoration(
                            color: Colors.white.withOpacity(0.05),
                            borderRadius: BorderRadius.circular(20),
                            border: Border.all(color: Colors.white10),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(Icons.tune, size: 16, color: Theme.of(context).primaryColor),
                              const SizedBox(width: 8),
                              Text(
                                _effectsManager.currentPreset.toString().split('.').last.toUpperCase(), // Display current preset
                                style: GoogleFonts.splineSans(fontSize: 10, color: Colors.white70, fontWeight: FontWeight.bold),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// Simple Painter to draw a waveform curve
class WaveformPainter extends CustomPainter {
  final Color color;
  WaveformPainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = 3
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final path = Path();
    final width = size.width;
    final height = size.height;

    // Simulate a waveform curve
    path.moveTo(0, height * 0.5);
    path.quadraticBezierTo(width * 0.1, height * 0.2, width * 0.2, height * 0.5);
    path.quadraticBezierTo(width * 0.3, height * 0.8, width * 0.4, height * 0.5);
    path.quadraticBezierTo(width * 0.5, height * 0.2, width * 0.6, height * 0.5);
    path.quadraticBezierTo(width * 0.7, height * 0.9, width * 0.8, height * 0.5);
    path.quadraticBezierTo(width * 0.9, height * 0.3, width, height * 0.5);

    // Draw main line
    canvas.drawPath(path, paint);
    
    // Draw shadow/reflection line
    final shadowPaint = Paint()
      ..color = color.withOpacity(0.3)
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
      
    final shadowPath = Path(); // Inverted curve slightly offset
    shadowPath.moveTo(0, height * 0.5);
    shadowPath.quadraticBezierTo(width * 0.1, height * 0.8, width * 0.2, height * 0.5);
    shadowPath.quadraticBezierTo(width * 0.3, height * 0.2, width * 0.4, height * 0.5);
    // ... simplified reflection
    
    // canvas.drawPath(shadowPath, shadowPaint); // Optional
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

// Extension to simulate animate().pulse() if flutter_animate not available
extension WidgetAnimation on Widget {
  Widget animate() => this; // Placeholder if package missing, logically would need StatefulWidget wrapper or package
  Widget pulse({required Duration duration}) => this;
}
