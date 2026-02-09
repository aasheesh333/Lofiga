import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // Background Glows
          Positioned(
            top: -100,
            left: -100,
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Theme.of(context).primaryColor.withOpacity(0.2),
                // blur applied usually via BackdropFilter or just opacity
              ),
            ),
          ),

          SafeArea(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Header
                Padding(
                  padding: const EdgeInsets.all(24.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Good Evening',
                            style: GoogleFonts.splineSans(
                              fontSize: 24,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          Text(
                            'Ready to chill?',
                            style: GoogleFonts.splineSans(
                              fontSize: 14,
                              color: Colors.grey[400],
                            ),
                          ),
                        ],
                      ),
                      CircleAvatar(
                        backgroundColor: const Color(0xFF2D243A),
                        child: IconButton(
                          icon: const Icon(Icons.settings, color: Colors.white),
                          onPressed: () {},
                        ),
                      ),
                    ],
                  ),
                ),

                // Main Content
                Expanded(
                  child: ListView(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    children: [
                       // Select Song Card
                       Container(
                         height: 200,
                         decoration: BoxDecoration(
                           borderRadius: BorderRadius.circular(16),
                           color: const Color(0xFF231B2E).withOpacity(0.6),
                           border: Border.all(color: Theme.of(context).primaryColor.withOpacity(0.3)),
                           boxShadow: [
                             BoxShadow(
                               color: Theme.of(context).primaryColor.withOpacity(0.1),
                               blurRadius: 20,
                               spreadRadius: -5,
                             )
                           ],
                         ),
                         child: Center(
                           child: Column(
                             mainAxisAlignment: MainAxisAlignment.center,
                             children: [
                               Container(
                                 width: 80,
                                 height: 80,
                                 decoration: BoxDecoration(
                                   color: Theme.of(context).primaryColor,
                                   shape: BoxShape.circle,
                                   boxShadow: [
                                     BoxShadow(color: Theme.of(context).primaryColor.withOpacity(0.4), blurRadius: 10),
                                   ],
                                 ),
                                 child: const Icon(Icons.add, size: 40, color: Colors.white),
                               ),
                               const SizedBox(height: 16),
                               const Text(
                                 'Select Song',
                                 style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                               ),
                               const SizedBox(height: 8),
                               Text(
                                 'Tap to import audio file (MP3, WAV)',
                                 style: TextStyle(color: Colors.grey[400], fontSize: 12),
                               ),
                             ],
                           ),
                         ),
                       ),
                       
                       const SizedBox(height: 32),
                       
                       // Recent Edits Header
                       Row(
                         mainAxisAlignment: MainAxisAlignment.spaceBetween,
                         children: [
                           const Text('Recent Edits', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                           TextButton(onPressed: (){}, child: const Text('See All')),
                         ],
                       ),
                       
                       const SizedBox(height: 16),
                       
                       // List Items
                       _buildRecentItem(
                         title: 'Midnight Jazz.mp3',
                         time: '2 min ago • 3:42',
                         color: Colors.purple.shade900,
                       ),
                       _buildRecentItem(
                         title: 'Study Session_v2.wav',
                         time: 'Yesterday • 12:05',
                         color: Colors.blue.shade900,
                       ),
                         _buildRecentItem(
                         title: 'Rainy Day.mp3',
                         time: 'Last Week • 4:20',
                         color: Colors.orange.shade900,
                       ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        backgroundColor: const Color(0xFF231B2E).withOpacity(0.9),
        selectedItemColor: Theme.of(context).primaryColor,
        unselectedItemColor: Colors.grey,
        type: BottomNavigationBarType.fixed,
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home), label: 'Home'),
          BottomNavigationBarItem(icon: Icon(Icons.tune), label: 'Editor'),
          BottomNavigationBarItem(icon: Icon(Icons.library_music), label: 'Library'),
          BottomNavigationBarItem(icon: Icon(Icons.settings), label: 'Settings'),
        ],
      ),
    );
  }

  Widget _buildRecentItem({required String title, required String time, required Color color}) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF231B2E),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white.withOpacity(0.05)),
      ),
      child: Row(
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: color,
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.play_arrow, color: Colors.white),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                const SizedBox(height: 4),
                Text(time, style: TextStyle(color: Colors.grey[400], fontSize: 12)),
              ],
            ),
          ),
          const Icon(Icons.more_vert, color: Colors.grey),
        ],
      ),
    );
  }
}
