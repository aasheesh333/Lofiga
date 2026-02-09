import 'package:flutter/material.dart';
import 'package:lofiga/theme/app_theme.dart';
import 'package:lofiga/screens/splash_screen.dart';
import 'package:lofiga/screens/home_screen.dart';

void main() {
  runApp(const LofigaApp());
}

class LofigaApp extends StatelessWidget {
  const LofigaApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Lofiga',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkTheme,
      home: const SplashScreen(),
      routes: {
        '/home': (context) => const HomeScreen(),
      },
    );
  }
}
