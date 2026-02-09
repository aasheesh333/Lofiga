import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:lofiga/logic/player_manager.dart';
import 'package:lofiga/screens/player_screen.dart';
import 'dart:ui';

class MiniPlayerBar extends StatelessWidget {
  const MiniPlayerBar({super.key});

  @override
  Widget build(BuildContext context) {
    return Consumer<PlayerManager>(
      builder: (context, playerManager, child) {
        if (!playerManager.hasTrack) {
          return const SizedBox.shrink(); // Hide if no track loaded
        }

        return GestureDetector(
          onTap: () {
            // Expand to full-screen player
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (context) => const PlayerScreen(),
                fullscreenDialog: true,
              ),
            );
          },
          child: Container(
            height: 70,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Theme.of(context).scaffoldBackgroundColor.withOpacity(0),
                  Theme.of(context).scaffoldBackgroundColor,
                ],
              ),
            ),
            child: ClipRRect(
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                child: Container(
                  decoration: BoxDecoration(
                    color: const Color(0xFF231B2E).withOpacity(0.9),
                    border: const Border(top: BorderSide(color: Colors.white10)),
                  ),
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: Row(
                    children: [
                      // Album Thumbnail
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(8),
                          gradient: LinearGradient(
                            colors: [
                              Theme.of(context).primaryColor,
                              Colors.blue.shade700,
                            ],
                          ),
                        ),
                        child: const Icon(Icons.music_note, color: Colors.white, size: 24),
                      ),
                      
                      const SizedBox(width: 12),
                      
                      // Track Title & Expand Button
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              playerManager.currentFileName ?? 'Unknown',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: GoogleFonts.splineSans(
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: Colors.white,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              _formatDuration(playerManager.position),
                              style: GoogleFonts.splineSans(
                                fontSize: 11,
                                color: Colors.white54,
                              ),
                            ),
                          ],
                        ),
                      ),
                      
                      // Expand Button (Up Arrow)
                      IconButton(
                        icon: const Icon(Icons.keyboard_arrow_up, color: Colors.white70),
                        onPressed: () {
                          Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (context) => const PlayerScreen(),
                              fullscreenDialog: true,
                            ),
                          );
                        },
                      ),
                      
                      // Play/Pause Button
                      IconButton(
                        icon: Icon(
                          playerManager.isPlaying ? Icons.pause : Icons.play_arrow,
                          color: Colors.white,
                        ),
                        onPressed: () {
                          playerManager.togglePlayPause();
                        },
                      ),
                      
                      // Skip Next (Placeholder)
                      IconButton(
                        icon: const Icon(Icons.skip_next, color: Colors.white70),
                        onPressed: () {
                          // TODO: Implement skip next
                        },
                      ),
                      
                      // More Options
                      IconButton(
                        icon: const Icon(Icons.more_vert, color: Colors.white70),
                        onPressed: () {
                          // TODO: Show options menu
                        },
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, '0');
    final minutes = twoDigits(duration.inMinutes.remainder(60));
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    return '$minutes:$seconds';
  }
}
