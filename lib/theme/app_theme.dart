import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lofiga/services/storage_service.dart';

class AppTheme {
  // Stitch Design Colors
  static const Color primaryColor = Color(0xFF993DF5); // Neon Purple
  static const Color accentColor = Color(0xFF3DF5E6);  // Cyan
  static const Color backgroundColor = Color(0xFF191022); // Deep Dark
  static const Color surfaceColor = Color(0xFF231B2E); // Surface Dark
  static const Color surfaceHighlight = Color(0xFF2D243A); // Lighter Surface

  static final ThemeData darkTheme = ThemeData(
    brightness: Brightness.dark,
    primaryColor: primaryColor,
    scaffoldBackgroundColor: backgroundColor,
    
    // Color Scheme
    colorScheme: const ColorScheme.dark(
      primary: primaryColor,
      secondary: accentColor,
      surface: surfaceColor,
      background: backgroundColor,
    ),

    // Typography
    textTheme: GoogleFonts.splineSansTextTheme(
      ThemeData.dark().textTheme,
    ).apply(
      bodyColor: Colors.white,
      displayColor: Colors.white,
    ),

    // Component Themes
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.transparent,
      elevation: 0,
      iconTheme: IconThemeData(color: Colors.white),
    ),
    useMaterial3: true,
  );

  static final ThemeData lightTheme = ThemeData(
    brightness: Brightness.light,
    primaryColor: primaryColor,
    scaffoldBackgroundColor: Colors.grey[100],
    
    // Color Scheme
    colorScheme: ColorScheme.light(
      primary: primaryColor,
      secondary: accentColor,
      surface: Colors.white,
      background: Colors.grey[100]!,
    ),

    // Typography
    textTheme: GoogleFonts.splineSansTextTheme(
      ThemeData.light().textTheme,
    ).apply(
      bodyColor: Colors.black87,
      displayColor: Colors.black87,
    ),

    // Component Themes
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.transparent,
      elevation: 0,
      iconTheme: IconThemeData(color: Colors.black87),
    ),
    useMaterial3: true,
  );
}

class ThemeProvider extends ChangeNotifier {
  ThemeMode _themeMode = ThemeMode.dark; // Default
  ThemeMode get themeMode => _themeMode;

  void setThemeMode(ThemeMode mode) {
    _themeMode = mode;
    notifyListeners();
  }
}

