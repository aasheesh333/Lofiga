import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:lofiga/theme/app_theme.dart';
import 'package:lofiga/logic/preset_manager.dart';
import 'package:lofiga/logic/audio_engine.dart';
import 'package:lofiga/screens/splash_screen.dart';
import 'package:lofiga/screens/home_screen.dart';

void main() {
  runApp(const LofigaApp());
}

class LofigaApp extends StatelessWidget {
  const LofigaApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        // Provide AudioEngine as a singleton service if needed, or just inside PresetManager
        Provider<AudioEngine>(create: (_) => AudioEngine()),

        // Provide PresetManager which depends on AudioEngine
        ChangeNotifierProxyProvider<AudioEngine, PresetManager>(
          create: (context) => PresetManager(context.read<AudioEngine>()),
          update: (context, engine, previous) => previous ?? PresetManager(engine),
        ),
        // Provide Theme context
        ChangeNotifierProvider<ThemeProvider>(
          create: (_) => ThemeProvider(),
        ),
      ],
      child: Consumer<ThemeProvider>(
        builder: (context, themeProvider, child) {
          return MaterialApp(
            title: 'Lofiga',
            debugShowCheckedModeBanner: false,
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: themeProvider.themeMode,
            home: const SplashScreen(),
            routes: {
              '/home': (context) => const HomeScreen(),
            },
          );
        },
      ),
    );
  }
}
