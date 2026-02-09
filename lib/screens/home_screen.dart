import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:file_picker/file_picker.dart';
import 'package:lofiga/screens/player_screen.dart';
import 'package:lofiga/screens/editor_screen.dart';
import 'package:lofiga/screens/library_screen.dart';
import 'package:lofiga/screens/settings_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _currentIndex = 0;

  @override
  Widget build(BuildContext context) {
    // List of screens for navigation
    final List<Widget> screens = [
      _buildHomeContent(),
      const EditorScreen(),
      const LibraryScreen(),
      const SettingsScreen(),
    ];

    return Scaffold(
      body: Stack(
        children: [
           // Global Background for all screens (optional, or specific to Home)
           // For now, let's keep the gradient/glow consistent if desired, 
           // but Editor/Library might want their own. 
           // The previous code had the background in the Scaffold body.
           // Let's assume the "Glow" is part of the common theme or specific to Home.
           // To keep it simple and consistent with previous design, we put the background here
           // ONLY if it's shared. But Editor/Listen might need clean backgrounds.
           // Let's render the selected screen.
           screens[_currentIndex],
        ],
      ),
      bottomNavigationBar: BottomNavigationBar(
        backgroundColor: const Color(0xFF231B2E).withOpacity(0.95), // Slightly more opaque
        selectedItemColor: Theme.of(context).primaryColor,
        unselectedItemColor: Colors.grey,
        type: BottomNavigationBarType.fixed,
        currentIndex: _currentIndex,
        onTap: (index) {
          setState(() {
            _currentIndex = index;
          });
        },
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.home), label: 'Home'),
          BottomNavigationBarItem(icon: Icon(Icons.tune), label: 'Editor'),
          BottomNavigationBarItem(icon: Icon(Icons.library_music), label: 'Library'),
          BottomNavigationBarItem(icon: Icon(Icons.settings), label: 'Settings'),
        ],
      ),
    );
  }

  // Extracted Home Tab Content
  Widget _buildHomeContent() {
    return Stack(
      children: [
         // Background Glows (Specific to Home)
          Positioned(
            top: -100,
            left: -100,
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Theme.of(context).primaryColor.withOpacity(0.2),
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
                          icon: const Icon(Icons.notifications, color: Colors.white), // Changed settings icon since we have a tab
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
                       GestureDetector(
                         onTap: () => _pickAudio(context),
                         child: Container(
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
    );
  }

  Future<void> _pickAudio(BuildContext context) async {
    try {
      FilePickerResult? result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['mp3', 'wav', 'm4a', 'flac', 'aac', 'ogg'],
      );

      if (result != null) {
        if (result.files.single.path != null) {
          String filePath = result.files.single.path!;
          String fileName = result.files.single.name;
          
          if (context.mounted) {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (context) => PlayerScreen(filePath: filePath, fileName: fileName),
              ),
            );
          }
        } else {
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Error: Selected file path is null (iOS iCloud file?)')),
            );
          }
        }
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error picking file: $e')),
        );
      }
    }
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
