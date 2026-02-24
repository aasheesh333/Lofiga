import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:just_audio/just_audio.dart';
import 'package:share_plus/share_plus.dart';
import 'package:lofiga/screens/player_editor_screen.dart';
import 'dart:ui'; // For BackdropFilter

class ExportedPlayerScreen extends StatefulWidget {
  final String filePath;
  final String fileName;

  const ExportedPlayerScreen({
    super.key,
    required this.filePath,
    required this.fileName,
  });

  @override
  State<ExportedPlayerScreen> createState() => _ExportedPlayerScreenState();
}

class _ExportedPlayerScreenState extends State<ExportedPlayerScreen> {
  final AudioPlayer _player = AudioPlayer();
  bool _isPlaying = false;
  Duration _duration = Duration.zero;
  Duration _position = Duration.zero;

  @override
  void initState() {
    super.initState();
    _initPlayer();
  }

  Future<void> _initPlayer() async {
    try {
      await _player.setFilePath(widget.filePath);
      _player.playerStateStream.listen((state) {
        if (mounted) {
          setState(() {
            _isPlaying = state.playing;
            if (state.processingState == ProcessingState.completed) {
              _isPlaying = false;
              _player.seek(Duration.zero);
              _player.pause();
            }
          });
        }
      });
      _player.positionStream.listen((pos) {
        if (mounted) setState(() => _position = pos);
      });
      _player.durationStream.listen((dur) {
        if (mounted) setState(() => _duration = dur ?? Duration.zero);
      });
      
      // Auto-play
      _player.play();
    } catch (e) {
      debugPrint('Error loading exported file: $e');
    }
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  void _togglePlay() {
    if (_isPlaying) {
      _player.pause();
    } else {
      _player.play();
    }
  }

  void _shareAudio() async {
    final text = 'Listen to my new chill Lofi track generated with Lofiga! 🎧✨ Download the app to make your own vibes: [App Link]';
    await Share.shareXFiles(
      [XFile(widget.filePath)],
      text: text,
      subject: 'My Lofiga Track',
    );
  }

  String _formatDuration(Duration d) {
    final min = d.inMinutes;
    final sec = (d.inSeconds % 60).toString().padLeft(2, '0');
    return '\$min:\$sec';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF140F1D).withOpacity(0.95), // very dark purple
      extendBodyBehindAppBar: true,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, color: Colors.white),
          onPressed: () => Navigator.pop(context),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.share, color: Colors.white),
            onPressed: _shareAudio,
            tooltip: 'Share',
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: Stack(
        children: [
          // Ambient Glow
          Positioned(
            top: 100,
            left: MediaQuery.of(context).size.width / 2 - 150,
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Theme.of(context).primaryColor.withOpacity(0.15),
                backgroundBlendMode: BlendMode.screen,
              ),
            ),
          ),
          BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 80, sigmaY: 80),
            child: Container(color: Colors.transparent),
          ),
          
          SafeArea(
            child: Column(
              children: [
                const SizedBox(height: 40),
                
                // Cover Art Placeholder
                Container(
                  width: MediaQuery.of(context).size.width * 0.8,
                  height: MediaQuery.of(context).size.width * 0.8,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(30),
                    boxShadow: [
                      BoxShadow(
                        color: Colors.black.withOpacity(0.4),
                        blurRadius: 30,
                        offset: const Offset(0, 15),
                      )
                    ],
                    gradient: const LinearGradient(
                      colors: [Color(0xFF2E203E), Color(0xFF1C1326)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                  ),
                  child: Center(
                    child: Icon(
                      Icons.music_note_rounded,
                      size: 100,
                      color: Theme.of(context).primaryColor.withOpacity(0.5),
                    ),
                  ),
                ),
                
                const SizedBox(height: 50),
                
                // Song Title
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 30),
                  child: Text(
                    widget.fileName,
                    maxLines: 2,
                    textAlign: TextAlign.center,
                    style: GoogleFonts.splineSans(
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                      color: Colors.white,
                    ),
                  ),
                ),
                
                const SizedBox(height: 8),
                Text(
                  'Generated with Lofiga',
                  style: GoogleFonts.splineSans(
                    fontSize: 14,
                    color: Colors.white54,
                  ),
                ),
                
                const SizedBox(height: 40),
                
                // Progress Bar
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                  child: Column(
                    children: [
                      SliderTheme(
                        data: SliderTheme.of(context).copyWith(
                          trackHeight: 4,
                          thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
                          overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
                          activeTrackColor: Theme.of(context).primaryColor,
                          inactiveTrackColor: Colors.white24,
                          thumbColor: Colors.white,
                        ),
                        child: Slider(
                          min: 0,
                          max: _duration.inMilliseconds.toDouble() > 0 ? _duration.inMilliseconds.toDouble() : 1.0,
                          value: _position.inMilliseconds.toDouble().clamp(0.0, _duration.inMilliseconds.toDouble() > 0 ? _duration.inMilliseconds.toDouble() : 1.0),
                          onChanged: (val) {
                            _player.seek(Duration(milliseconds: val.toInt()));
                          },
                        ),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(_formatDuration(_position), style: const TextStyle(color: Colors.white54, fontSize: 12)),
                            Text(_formatDuration(_duration), style: const TextStyle(color: Colors.white54, fontSize: 12)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                
                const Spacer(),
                
                // Play Controls
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      iconSize: 40,
                      icon: const Icon(Icons.replay_10, color: Colors.white70),
                      onPressed: () {
                        final newPos = _position - const Duration(seconds: 10);
                        _player.seek(newPos < Duration.zero ? Duration.zero : newPos);
                      },
                    ),
                    const SizedBox(width: 20),
                    GestureDetector(
                      onTap: _togglePlay,
                      child: Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: Theme.of(context).primaryColor,
                          boxShadow: [
                            BoxShadow(
                              color: Theme.of(context).primaryColor.withOpacity(0.4),
                              blurRadius: 20,
                              offset: const Offset(0, 5),
                            )
                          ],
                        ),
                        child: Icon(
                          _isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded,
                          size: 40,
                          color: Colors.white,
                        ),
                      ),
                    ),
                    const SizedBox(width: 20),
                    IconButton(
                      iconSize: 40,
                      icon: const Icon(Icons.forward_10, color: Colors.white70),
                      onPressed: () {
                        final newPos = _position + const Duration(seconds: 10);
                        _player.seek(newPos > _duration ? _duration : newPos);
                      },
                    ),
                  ],
                ),
                
                const SizedBox(height: 30),
                
                // Edit in Studio Button
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                  child: ElevatedButton.icon(
                    onPressed: () {
                      _player.stop();
                      Navigator.pushReplacement(
                        context,
                        MaterialPageRoute(
                          builder: (_) => PlayerEditorScreen(
                            filePath: widget.filePath,
                            fileName: widget.fileName,
                          ),
                        ),
                      );
                    },
                    icon: const Icon(Icons.tune, color: Colors.white),
                    label: Text(
                      'Edit in Studio',
                      style: GoogleFonts.splineSans(fontSize: 16, fontWeight: FontWeight.bold, color: Colors.white),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.white.withOpacity(0.1),
                      minimumSize: const Size(double.infinity, 56),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      elevation: 0,
                    ),
                  ),
                ),
                
                const SizedBox(height: 40),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
