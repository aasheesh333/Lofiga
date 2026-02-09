import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:lofiga/screens/home_screen.dart';
import 'dart:math' as math;

class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
       duration: const Duration(seconds: 10),
       vsync: this,
    )..repeat();

    // Navigate to Home after 3 seconds
    Future.delayed(const Duration(seconds: 3), () {
      if (mounted) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(builder: (context) => const HomeScreen()),
        );
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // Background Gradient
          Container(
            decoration: const BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  Color(0xFF0F172A),
                  Color(0xFF191022),
                  Color(0xFF2E1065),
                ],
              ),
            ),
          ),
          
          // Ambient Glow
          Center(
            child: Transform(
              transform: Matrix4.identity(), 
              child: Container(
                width: 600,
                height: 600,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: const Color(0xFF993DF5).withOpacity(0.2),
                ),
              ),
            ), 
          ),
          
          Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Vinyl Record Animation
                AnimatedBuilder(
                  animation: _controller,
                  builder: (_, child) {
                    return Transform.rotate(
                      angle: _controller.value * 2 * math.pi,
                      child: child,
                    );
                  },
                  child: _buildVinylRecord(),
                ),
                
                const SizedBox(height: 48),
                
                // Text
                Text(
                  'Lofiga',
                  style: GoogleFonts.splineSans(
                    fontSize: 48,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                    shadows: [
                      const Shadow(color: Colors.black45, blurRadius: 10, offset: Offset(0, 4)),
                    ],
                  ),
                ),
                Text(
                  'Turn Any Song Into Lofi',
                  style: GoogleFonts.splineSans(
                    fontSize: 18,
                    color: Colors.white60,
                    letterSpacing: 1.2,
                  ),
                ),
              ],
            ),
          ),
          
          // Loader
          Positioned(
            bottom: 48,
            left: 0,
            right: 0,
            child: Center(
              child: Column(
                children: [
                   const Icon(Icons.music_note, color: Color(0xFF993DF5), size: 36),
                   const SizedBox(height: 8),
                   Text('VERSION 1.0', style: GoogleFonts.notoSans(fontSize: 10, letterSpacing: 2, color: Colors.white30)),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildVinylRecord() {
    return Container(
      width: 280,
      height: 280,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: const Color(0xFF121212),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.5),
            blurRadius: 20,
            spreadRadius: 5,
          ),
        ],
        border: Border.all(color: Colors.white.withOpacity(0.05), width: 1),
      ),
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Grooves would be complex gradients, skipping for simplicity or using image
          // Inner Label
          Container(
            width: 100,
            height: 100,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
              gradient: LinearGradient(
                colors: [Color(0xFF993DF5), Color(0xFF581C87)],
              ),
            ),
          ),
          // Center Hole
          Container(
            width: 12,
            height: 12,
            decoration: const BoxDecoration(
               color: Color(0xFF121212),
               shape: BoxShape.circle,
            ),
          ),
        ],
      ),
    );
  }
}
