import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class LibraryScreen extends StatelessWidget {
  const LibraryScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.library_music, size: 80, color: Theme.of(context).primaryColor.withOpacity(0.5)),
          const SizedBox(height: 16),
          Text(
            'Library',
            style: GoogleFonts.splineSans(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Your saved mixes will appear here.',
            style: GoogleFonts.splineSans(color: Colors.white54),
          ),
        ],
      ),
    );
  }
}
