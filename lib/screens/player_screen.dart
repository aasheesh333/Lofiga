import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:lofiga/logic/player_manager.dart';
import 'package:lofiga/screens/export_screen.dart';
import 'dart:ui'; // For BackdropFilter

class PlayerScreen extends StatefulWidget {
  const PlayerScreen({super.key});

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  // State to track if effects panel is expanded
  bool _showEffects = false;

  void _toggleEffects() {
    setState(() {
      _showEffects = !_showEffects;
    });
  }

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
              // --- 1. Ambient Background Layer ---
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
              BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 80, sigmaY: 80),
                child: Container(color: Colors.transparent),
              ),

              // --- 2. Main Content Layer ---
              SafeArea(
                bottom: false,
                child: Column(
                  children: [
                    // A. Top Navigation (Fixed)
                    _buildTopNav(context, playerManager),

                    // B. Middle Section (Album Art + Controls) - Flexible
                    Expanded(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          // 1. Album Art & Info (Animated Size)
                          Expanded(
                            flex: _showEffects ? 2 : 4, // Shuffle space based on state
                            child: LayoutBuilder(
                              builder: (context, constraints) {
                                // Calculate scale based on available height
                                double size = constraints.maxHeight * (_showEffects ? 0.6 : 0.8);
                                size = size.clamp(100.0, 300.0); // Limits
                                
                                return Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    // Album Art
                                    AnimatedContainer(
                                      duration: const Duration(milliseconds: 300),
                                      curve: Curves.easeOutCubic,
                                      width: size,
                                      height: size,
                                      decoration: BoxDecoration(
                                        shape: BoxShape.circle, // Rounded square or circle? Design implies rounded square usually but code was circle. Let's keep circle or switch to rounded rect if requested. Code was circle.
                                        // keeping consistent with previous code using circle art
                                        color: Colors.black26, 
                                        boxShadow: [
                                          BoxShadow(
                                            color: Theme.of(context).primaryColor.withOpacity(0.3),
                                            blurRadius: _showEffects ? 10 : 30, // Reduce glow when minimized
                                            spreadRadius: 0,
                                          ),
                                        ],
                                      ),
                                      child: Container(
                                        decoration: BoxDecoration(
                                          shape: BoxShape.circle,
                                          gradient: LinearGradient(
                                            begin: Alignment.topLeft,
                                            end: Alignment.bottomRight,
                                            colors: [
                                              Colors.purple.shade800,
                                              Colors.blue.shade900,
                                            ],
                                          ),
                                        ),
                                        child: Icon(Icons.music_note, size: size * 0.4, color: Colors.white24),
                                      ),
                                    ),
                                    
                                    SizedBox(height: _showEffects ? 16 : 32),

                                    // Title & Waveform
                                    AnimatedOpacity(
                                      duration: const Duration(milliseconds: 200),
                                      opacity: _showEffects ? 0.8 : 1.0,
                                      child: Column(
                                        children: [
                                          Padding(
                                            padding: const EdgeInsets.symmetric(horizontal: 32),
                                            child: Text(
                                              playerManager.currentFileName ?? 'Unknown Track',
                                              textAlign: TextAlign.center,
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: GoogleFonts.splineSans(
                                                fontSize: _showEffects ? 18 : 24, // Smaller font when minimized
                                                fontWeight: FontWeight.bold,
                                                color: Colors.white,
                                              ),
                                            ),
                                          ),
                                          if (!_showEffects) ...[ // Hide these details when effects open to save space
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
                                            SizedBox(
                                              height: 40,
                                              width: 200,
                                              child: CustomPaint(
                                                painter: WaveformPainter(color: Theme.of(context).primaryColor),
                                              ),
                                            ),
                                          ],
                                        ],
                                      ),
                                    ),
                                  ],
                                );
                              }
                            ),
                          ),

                          // 2. Playback Controls (Always Visible, Fixed Height roughly)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                // Slider
                                SliderTheme(
                                  data: SliderTheme.of(context).copyWith(
                                    activeTrackColor: Theme.of(context).colorScheme.secondary,
                                    inactiveTrackColor: Colors.white10,
                                    thumbColor: Colors.white,
                                    trackHeight: 2,
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
                                  padding: const EdgeInsets.symmetric(horizontal: 10),
                                  child: Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text(_formatDuration(playerManager.position), style: GoogleFonts.splineSans(fontSize: 10, color: Colors.white38)),
                                      Text(_formatDuration(playerManager.duration), style: GoogleFonts.splineSans(fontSize: 10, color: Colors.white38)),
                                    ],
                                  ),
                                ),
                                
                                const SizedBox(height: 10),

                                // Buttons
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    IconButton(onPressed: (){}, icon: const Icon(Icons.shuffle, color: Colors.white38)),
                                    Row(
                                      children: [
                                        IconButton(onPressed: (){}, icon: const Icon(Icons.skip_previous, color: Colors.white, size: 30)),
                                        const SizedBox(width: 20),
                                        GestureDetector(
                                          onTap: playerManager.togglePlayPause,
                                          child: Container(
                                            padding: const EdgeInsets.all(16),
                                            decoration: BoxDecoration(
                                              shape: BoxShape.circle,
                                              color: Theme.of(context).primaryColor,
                                              boxShadow: [
                                                BoxShadow(color: Theme.of(context).primaryColor.withOpacity(0.5), blurRadius: 15),
                                              ],
                                            ),
                                            child: Icon(
                                              playerManager.isPlaying ? Icons.pause : Icons.play_arrow,
                                              color: Colors.white,
                                              size: 28,
                                            ),
                                          ),
                                        ),
                                        const SizedBox(width: 20),
                                        IconButton(onPressed: (){}, icon: const Icon(Icons.skip_next, color: Colors.white, size: 30)),
                                      ],
                                    ),
                                    IconButton(onPressed: (){}, icon: const Icon(Icons.repeat, color: Colors.white38)),
                                  ],
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),

                    // C. Expandable Effects Panel (Bottom)
                    AnimatedContainer(
                      duration: const Duration(milliseconds: 300),
                      curve: Curves.easeInOutCubic,
                      height: _showEffects ? 380 : 80, // Collapsed vs Expanded Height
                      decoration: BoxDecoration(
                        color: const Color(0xFF231B2E).withOpacity(0.95), // Surface color
                        borderRadius: const BorderRadius.only(
                          topLeft: Radius.circular(32),
                          topRight: Radius.circular(32),
                        ),
                        border: const Border(top: BorderSide(color: Colors.white10)),
                        boxShadow: [
                          BoxShadow(color: Colors.black.withOpacity(0.5), blurRadius: 20, offset: const Offset(0, -5)),
                        ],
                      ),
                      child: ClipRRect(
                        borderRadius: const BorderRadius.only(topLeft: Radius.circular(32), topRight: Radius.circular(32)),
                        child: Column(
                          children: [
                            // 1. Header Handle (Tap to Toggle)
                            GestureDetector(
                              onTap: _toggleEffects,
                              behavior: HitTestBehavior.opaque,
                              child: Container(
                                padding: const EdgeInsets.symmetric(vertical: 16),
                                width: double.infinity,
                                child: Column(
                                  children: [
                                    Container(
                                      width: 40, height: 4,
                                      decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2)),
                                    ),
                                    const SizedBox(height: 12),
                                    Row(
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      children: [
                                        const Icon(Icons.tune, size: 14, color: Colors.white70),
                                        const SizedBox(width: 8),
                                        Text(
                                          'LIVE EFFECTS',
                                          style: GoogleFonts.splineSans(
                                            fontSize: 12,
                                            fontWeight: FontWeight.bold,
                                            letterSpacing: 2,
                                            color: Colors.white70,
                                          ),
                                        ),
                                        const SizedBox(width: 8),
                                        Icon(_showEffects ? Icons.keyboard_arrow_down : Icons.keyboard_arrow_up, size: 16, color: Colors.white38),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                            ),

                            // 2. Expanded Content
                            if (_showEffects)
                              Expanded(
                                child: SingleChildScrollView(
                                  padding: const EdgeInsets.symmetric(horizontal: 24),
                                  child: Column(
                                    children: [
                                      // Presets
                                      _buildPresetSelector(context, playerManager),
                                      const SizedBox(height: 32),
                                      
                                      // Speed Control
                                      _buildSliderControl(
                                        context, 
                                        'Speed', 
                                        '${(playerManager.speed * 100).toInt()}%', 
                                        playerManager.speed, 
                                        0.5, 
                                        2.0, 
                                        playerManager.setSpeed
                                      ),
                                      
                                      const SizedBox(height: 24),
                                      
                                      // Pitch Control
                                      _buildSliderControl(
                                        context, 
                                        'Pitch', 
                                        '${((playerManager.pitch - 1.0) * 12).toStringAsFixed(1)} ST', 
                                        playerManager.pitch, 
                                        0.5, 
                                        2.0, 
                                        playerManager.setPitch
                                      ),

                                      const SizedBox(height: 32),
                                    ],
                                  ),
                                ),
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
        );
      },
    );
  }

  Widget _buildTopNav(BuildContext context, PlayerManager playerManager) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          IconButton(
            icon: const Icon(Icons.keyboard_arrow_down, color: Colors.white),
            onPressed: () => Navigator.pop(context),
            style: IconButton.styleFrom(backgroundColor: Colors.white.withOpacity(0.05)),
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
            icon: const Icon(Icons.ios_share, color: Colors.white, size: 18),
            onPressed: () {
               Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => ExportScreen(fileName: playerManager.currentFileName ?? 'Track'),
                ),
              );
            },
            style: IconButton.styleFrom(backgroundColor: Colors.white.withOpacity(0.05)),
          ),
        ],
      ),
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
          style: GoogleFonts.splineSans(fontSize: 12, color: isSelected ? Colors.white : Colors.white70, fontWeight: FontWeight.bold),
        ),
      ),
    );
  }

  Widget _buildSliderControl(BuildContext context, String label, String valueLabel, double value, double min, double max, Function(double) onChanged) {
    return Column(
      children: [
        Row(
          children: [
            Text(label, style: GoogleFonts.splineSans(fontSize: 14, color: Colors.white70)),
            const Spacer(),
            Text(valueLabel, style: GoogleFonts.splineSans(fontSize: 14, color: Colors.white, fontWeight: FontWeight.bold)),
          ],
        ),
        const SizedBox(height: 8),
        SliderTheme(
          data: SliderTheme.of(context).copyWith(
            activeTrackColor: Theme.of(context).primaryColor,
            inactiveTrackColor: Colors.white10,
            thumbColor: Colors.white,
            trackHeight: 4,
            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
            overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
          ),
          child: Slider(
            min: min,
            max: max,
            value: value,
            onChanged: onChanged,
          ),
        ),
      ],
    );
  }
}

class WaveformPainter extends CustomPainter {
  final Color color;
  WaveformPainter({required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color.withOpacity(0.5)
      ..strokeWidth = 3
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final path = Path();
    final width = size.width;
    final height = size.height;

    // Simulate simple waveform
    path.moveTo(0, height * 0.5);
    for (double i = 0; i < width; i += 10) {
      path.quadraticBezierTo(
        i + 5, 
        height * 0.5 + (i % 20 == 0 ? 10 : -10), 
        i + 10, 
        height * 0.5
      );
    }
    
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
