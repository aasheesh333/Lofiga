import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:just_audio/just_audio.dart';
import 'dart:ui'; // For BackdropFilter

class EditorScreen extends StatefulWidget {
  const EditorScreen({super.key});

  @override
  State<EditorScreen> createState() => _EditorScreenState();
}

class _EditorScreenState extends State<EditorScreen> {
  // Master Decks Values
  double _speed = 1.0;
  double _pitch = 1.0;
  
  // Atmosphere Values (Simulation)
  double _reverb = 0.3;
  double _bass = 0.6;
  double _delay = 0.2;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: Stack(
        children: [
           // Ambient Background Glow relative to Editor
           Positioned(
             top: 100,
             right: -50,
             child: Container(
               width: 300,
               height: 300,
               decoration: BoxDecoration(
                 shape: BoxShape.circle,
                 color: Theme.of(context).primaryColor.withOpacity(0.1),
                 backgroundBlendMode: BlendMode.screen,
               ),
             ),
           ),
           BackdropFilter(
             filter: ImageFilter.blur(sigmaX: 50, sigmaY: 50),
             child: Container(color: Colors.transparent),
           ),

           SafeArea(
             bottom: false,
             child: Column(
               children: [
                 // Header
                 Padding(
                   padding: const EdgeInsets.all(24.0),
                   child: Row(
                     mainAxisAlignment: MainAxisAlignment.spaceBetween,
                     children: [
                       Row(
                         children: [
                           IconButton(
                             icon: const Icon(Icons.arrow_back, color: Colors.white70),
                             onPressed: () {
                               // Start navigation back logic if needed, 
                               // but here it's a tab, so maybe just show Title
                             }, 
                           ),
                           const SizedBox(width: 8),
                           Text(
                             'Effects',
                             style: GoogleFonts.splineSans(
                               fontSize: 24,
                               fontWeight: FontWeight.bold,
                               color: Colors.white,
                             ),
                           ),
                         ],
                       ),
                       Container(
                         padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                         decoration: BoxDecoration(
                           color: Colors.white.withOpacity(0.05),
                           borderRadius: BorderRadius.circular(20),
                           border: Border.all(color: Colors.white10),
                         ),
                         child: Row(
                           children: [
                             Text(
                               'LIVE',
                               style: GoogleFonts.splineSans(
                                 fontSize: 10,
                                 fontWeight: FontWeight.bold,
                                 color: Theme.of(context).primaryColor,
                                 letterSpacing: 1,
                               ),
                             ),
                             const SizedBox(width: 8),
                             Container(
                               width: 8, height: 8,
                               decoration: BoxDecoration(
                                 color: Theme.of(context).primaryColor,
                                 shape: BoxShape.circle,
                                 boxShadow: [
                                   BoxShadow(color: Theme.of(context).primaryColor, blurRadius: 5),
                                 ],
                               ),
                             ),
                           ],
                         ),
                       ),
                     ],
                   ),
                 ),

                 Expanded(
                   child: ListView(
                     padding: const EdgeInsets.symmetric(horizontal: 24.0),
                     children: [
                       // Master Decks Section
                       _buildSectionHeader('MASTER DECKS', Icons.equalizer),
                       const SizedBox(height: 16),
                       Row(
                         children: [
                           Expanded(child: _buildVerticalSlider('Speed', '75%', _speed, (v) => setState(() => _speed = v))),
                           const SizedBox(width: 16),
                           Expanded(child: _buildVerticalSlider('Pitch', '-2 ST', _pitch, (v) => setState(() => _pitch = v))),
                         ],
                       ),

                       const SizedBox(height: 32),

                       // Atmosphere Section
                       _buildSectionHeader('ATMOSPHERE', Icons.tune),
                       const SizedBox(height: 16),
                       _buildHorizontalSlider('Space (Reverb)', 'High', _reverb, (v) => setState(() => _reverb = v)),
                       _buildHorizontalSlider('Deepness (Bass)', '+6dB', _bass, (v) => setState(() => _bass = v)),
                       _buildHorizontalSlider('Delay (Echo)', '250ms', _delay, (v) => setState(() => _delay = v)),

                       const SizedBox(height: 32),

                       // Presets Section
                       _buildSectionHeader('PRESETS', Icons.grid_view),
                       const SizedBox(height: 16),
                       SingleChildScrollView(
                         scrollDirection: Axis.horizontal,
                         child: Row(
                           children: [
                             _buildPresetChip('None', Icons.block, false),
                             _buildPresetChip('Chill', Icons.bedtime, true),
                             _buildPresetChip('Sad', Icons.cloudy_snowing, false),
                             _buildPresetChip('Night Drive', Icons.local_taxi, false),
                             _buildPresetChip('Vinyl', Icons.album, false),
                           ],
                         ),
                       ),
                       
                       const SizedBox(height: 100), // Bottom padding
                     ],
                   ),
                 ),
               ],
             ),
           ),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title, IconData icon) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          title,
          style: GoogleFonts.splineSans(
            fontSize: 12,
            fontWeight: FontWeight.bold,
            letterSpacing: 1.5,
            color: Colors.white60,
          ),
        ),
        Icon(icon, color: Colors.white38, size: 18),
      ],
    );
  }

  Widget _buildVerticalSlider(String label, String valueLabel, double value, ValueChanged<double> onChanged) {
    return Container(
      height: 220,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.05),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white10),
      ),
      child: Column(
        children: [
          Expanded(
            child: Stack(
              alignment: Alignment.bottomCenter,
              children: [
                Container(
                  width: 40,
                  decoration: BoxDecoration(
                    color: const Color(0xFF231B2E),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: Colors.white10),
                  ),
                ),
                Container(
                  width: 40,
                  height: 160 * value, // visual height based on value
                  decoration: BoxDecoration(
                    color: Theme.of(context).primaryColor,
                    borderRadius: BorderRadius.circular(10),
                    boxShadow: [
                      BoxShadow(color: Theme.of(context).primaryColor.withOpacity(0.4), blurRadius: 10),
                    ],
                  ),
                ),
                // Invisible slider for interaction
                RotatedBox(
                  quarterTurns: -1,
                  child: SliderTheme(
                    data: SliderTheme.of(context).copyWith(
                      thumbShape: SliderComponentShape.noThumb,
                      overlayShape: SliderComponentShape.noOverlay,
                      activeTrackColor: Colors.transparent,
                      inactiveTrackColor: Colors.transparent,
                    ),
                    child: Slider(
                      value: value,
                      onChanged: onChanged,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Text(valueLabel, style: GoogleFonts.splineSans(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.white)),
          Text(label, style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white54)),
        ],
      ),
    );
  }

  Widget _buildHorizontalSlider(String label, String valueLabel, double value, ValueChanged<double> onChanged) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 24.0),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(label, style: GoogleFonts.splineSans(fontWeight: FontWeight.w500, fontSize: 14, color: Colors.white)),
              Text(valueLabel, style: GoogleFonts.splineSans(fontWeight: FontWeight.bold, fontSize: 12, color: Colors.white60)),
            ],
          ),
          const SizedBox(height: 8),
          SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: Theme.of(context).primaryColor,
              inactiveTrackColor: const Color(0xFF231B2E),
              trackHeight: 6,
              thumbColor: Colors.white,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 8),
              overlayColor: Theme.of(context).primaryColor.withOpacity(0.2),
              overlayShape: const RoundSliderOverlayShape(overlayRadius: 16),
            ),
            child: Slider(
              value: value,
              onChanged: onChanged,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPresetChip(String label, IconData icon, bool isSelected) {
    return Container(
      margin: const EdgeInsets.only(right: 12),
      child: InkWell(
        onTap: () {},
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
          decoration: BoxDecoration(
            color: isSelected ? Theme.of(context).primaryColor : Colors.transparent,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: isSelected ? Theme.of(context).primaryColor : Colors.white24,
            ),
            boxShadow: isSelected ? [
              BoxShadow(color: Theme.of(context).primaryColor.withOpacity(0.3), blurRadius: 15),
            ] : [],
          ),
          child: Row(
            children: [
              Icon(icon, size: 18, color: isSelected ? Colors.white : Colors.white60),
              const SizedBox(width: 8),
              Text(
                label,
                style: GoogleFonts.splineSans(
                  fontWeight: FontWeight.w600,
                  fontSize: 14,
                  color: isSelected ? Colors.white : Colors.white60,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
