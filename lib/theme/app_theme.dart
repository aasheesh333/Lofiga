import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppTheme {
  static const Color primary = Color(0xFF993DF5);
  static const Color backgroundDark = Color(0xFF191022);
  static const Color surfaceDark = Color(0xFF231B2E);
  static const Color surfaceHighlight = Color(0xFF2D243A);
  
  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: backgroundDark,
      primaryColor: primary,
      colorScheme: const ColorScheme.dark(
        primary: primary,
        surface: surfaceDark,
        background: backgroundDark,
      ),
      textTheme: GoogleFonts.splineSansTextTheme(ThemeData.dark().textTheme).apply(
        bodyColor: Colors.white,
        displayColor: Colors.white,
      ),
      iconTheme: const IconThemeData(color: Colors.white),
    );
  }
}
