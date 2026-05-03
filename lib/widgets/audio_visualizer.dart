import 'dart:math' as math;
import 'package:flutter/material.dart';

/// A custom audio visualizer widget that displays a waveform-like animation
/// Uses the provided animation value for smooth visual effects
class AudioVisualizer extends StatelessWidget {
  final double animationValue;
  final double height;
  final Color color;
  final int barCount;

  const AudioVisualizer({
    Key? key,
    required this.animationValue,
    this.height = 80.0,
    this.color = const Color(0xFF993DF5),
    this.barCount = 32,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      size: Size(double.infinity, height),
      painter: _AudioVisualizerPainter(
        animationValue: animationValue,
        barCount: barCount,
        color: color,
      ),
    );
  }
}

class _AudioVisualizerPainter extends CustomPainter {
  final double animationValue;
  final int barCount;
  final Color color;

  _AudioVisualizerPainter({
    required this.animationValue,
    required this.barCount,
    required this.color,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;

    final barWidth = size.width / barCount;
    final maxHeight = size.height * 0.8; // Leave some padding
    final centerY = size.height / 2;

    // Generate animated waveform data
    // This uses the animation value to create a moving waveform effect
    // In a real implementation, this would come from actual audio FFT data
    List<double> heights = [];
    for (int i = 0; i < barCount; i++) {
      // Create a waveform that moves with the animation
      double phase = (i / barCount) * 2 * math.pi;
      double offset = animationValue * 2 * math.pi; // Use animation for movement
      
      // Combine multiple sine waves for more interesting waveform
      double value = 
          0.3 * math.sin(phase * 2 + offset) +
          0.2 * math.sin(phase * 3 + offset * 1.5) +
          0.1 * math.sin(phase * 5 + offset * 2.2) +
          0.1 * (math.random() - 0.5); // Add some noise for realism
      
      // Normalize to 0-1 range and apply to height
      double normalizedHeight = ((value + 1) / 2).clamp(0.0, 1.0);
      heights.add(normalizedHeight * maxHeight);
    }

    // Draw bars
    for (int i = 0; i < barCount; i++) {
      final x = i * barWidth + barWidth * 0.2; // Add some spacing
      final barHeight = heights[i];
      final rectWidth = barWidth * 0.6; // Make bars thinner than space
      
      final rect = Rect.fromLTWH(
        x,
        centerY - barHeight / 2,
        rectWidth,
        barHeight,
      );
      
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          rect,
          Radius.circular(2.0),
        ),
        paint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _AudioVisualizerPainter oldDelegate) {
    return oldDelegate.animationValue != animationValue ||
           oldDelegate.barCount != barCount ||
           oldDelegate.color != color;
  }
}