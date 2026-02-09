import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:lofiga/logic/player_manager.dart';
import 'package:lofiga/screens/export_screen.dart';
import 'dart:ui'; // For BackdropFilter

class PlayerScreen extends StatelessWidget {
  const PlayerScreen({super.key});

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');
    final minutes = twoDigits(duration.inMinutes.remainder(60));
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    return '$minutes:$seconds';
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<PlayerManager>(
      builder: (context, playerManager, child) {
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
                                  MaterialPageRoute(
                                    builder: (context) => ExportScreen(fileName: playerManager.currentFileName ?? 'Track'),
                                  ),
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
                              ),
                              
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
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 32),
                            child: Text(
                              playerManager.currentFileName ?? 'Unknown Track',
                              textAlign: TextAlign.center,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: GoogleFonts.splineSans(
                                fontSize: 24,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                              ),
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
                              playerManager.currentPreset.toString().split('.').last.toUpperCase(),
                              style: GoogleFonts.splineSans(
                                fontSize: 10,
                                fontWeight: FontWeight.bold,
                                letterSpacing: 1.5,
                                color: Colors.white70,
                              ),
                            ),
                          ),
                          
                          const SizedBox(height: 32),

                          // Waveform Visualizer
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
                                  max: playerManager.duration.inSeconds.toDouble(),
                                  value: playerManager.position.inSeconds.toDouble().clamp(0, playerManager.duration.inSeconds.toDouble()),
                                  onChanged: (value) async {
                                    final position = Duration(seconds: value.toInt());
                                    await playerManager.seek(position);
                                  },
                                ),
                              ),
                              Padding(
                                padding: const EdgeInsets.symmetric(horizontal: 8),
                                child: Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text(_formatDuration(playerManager.position), style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white38)),
                                    Text(_formatDuration(playerManager.duration), style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white38)),
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
                                      playerManager.togglePlayPause();
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
                                        playerManager.isPlaying ? Icons.pause : Icons.play_arrow,
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
                          
                          // Preset Selector
                          _buildPresetSelector(context, playerManager),

                          const SizedBox(height: 24),

                          // Live Effects Sliders
                          _buildEffectsPanel(context, playerManager),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildPresetSelector(BuildContext context, PlayerManager playerManager) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        _buildPresetChip(context, 'None', AudioEffectPreset.none, playerManager),
        const SizedBox(width: 12),
        _buildPresetChip(context, 'Lofi', AudioEffectPreset.lofi, playerManager),
        const SizedBox(width: 12),
        _buildPresetChip(context, 'Nightcore', AudioEffectPreset.nightcore, playerManager),
      ],
    );
  }

  Widget _buildPresetChip(BuildContext context, String label, AudioEffectPreset preset, PlayerManager playerManager) {
    final isSelected = playerManager.currentPreset == preset;
    
    return GestureDetector(
      onTap: () => playerManager.applyPreset(preset),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: isSelected ? Theme.of(context).primaryColor : Colors.white.withOpacity(0.05),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: isSelected ? Theme.of(context).primaryColor : Colors.white10),
          boxShadow: isSelected ? [
            BoxShadow(color: Theme.of(context).primaryColor.withOpacity(0.3), blurRadius: 10),
          ] : [],
        ),
        child: Text(
          label,
          style: GoogleFonts.splineSans(fontSize: 10, color: isSelected ? Colors.white : Colors.white70, fontWeight: FontWeight.bold),
        ),
      ),
    );
  }

  Widget _buildEffectsPanel(BuildContext context, PlayerManager playerManager) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.05),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        children: [
          Row(
            children: [
              const Icon(Icons.tune, color: Colors.white70, size: 16),
              const SizedBox(width: 8),
              Text('LIVE EFFECTS', style: GoogleFonts.splineSans(fontSize: 10, fontWeight: FontWeight.bold, color: Colors.white70, letterSpacing: 1.5)),
            ],
          ),
          const SizedBox(height: 16),
          
          // Speed Slider
          Row(
            children: [
              Text('Speed', style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white70)),
              const Spacer(),
              Text('${(playerManager.speed * 100).toInt()}%', style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white, fontWeight: FontWeight.bold)),
            ],
          ),
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: Theme.of(context).primaryColor,
              inactiveTrackColor: Colors.white10,
              thumbColor: Colors.white,
              trackHeight: 3,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 5),
            ),
            child: Slider(
              min: 0.5,
              max: 2.0,
              value: playerManager.speed,
              onChanged: (value) {
                playerManager.setSpeed(value); // LIVE update
              },
            ),
          ),

          const SizedBox(height: 12),

          // Pitch Slider
          Row(
            children: [
              Text('Pitch', style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white70)),
              const Spacer(),
              Text('${((playerManager.pitch - 1.0) * 12).toStringAsFixed(1)} ST', style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white, fontWeight: FontWeight.bold)),
            ],
          ),
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: Theme.of(context).primaryColor,
              inactiveTrackColor: Colors.white10,
              thumbColor: Colors.white,
              trackHeight: 3,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 5),
            ),
            child: Slider(
              min: 0.5,
              max: 2.0,
              value: playerManager.pitch,
              onChanged: (value) {
                playerManager.setPitch(value); // LIVE update
              },
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
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
