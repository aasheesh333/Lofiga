import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.settings, size: 80, color: Theme.of(context).primaryColor.withOpacity(0.5)),
          const SizedBox(height: 16),
          Text(
            'Settings',
            style: GoogleFonts.splineSans(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'App preferences and configuration.',
            style: GoogleFonts.splineSans(color: Colors.white54),
          ),
        ],
      ),
    );
  }
}
