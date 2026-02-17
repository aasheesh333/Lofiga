import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:file_picker/file_picker.dart';
import 'package:provider/provider.dart';
import 'package:lofiga/logic/preset_manager.dart';
import 'package:lofiga/screens/player_editor_screen.dart';
import 'package:lofiga/screens/library_screen.dart';
import 'package:lofiga/screens/settings_screen.dart';
import 'package:lofiga/screens/settings_screen.dart';
import 'package:lofiga/services/storage_service.dart';
import 'package:intl/intl.dart'; 
import 'dart:ui'; // For BackdropFilter

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _currentIndex = 0;
  List<SavedConfig> _recentEdits = [];

  @override
  void initState() {
    super.initState();
    _loadRecentEdits();
  }

  Future<void> _loadRecentEdits() async {
    final storage = StorageService();
    final edits = await storage.loadAllConfigs();
    // Sort by savedAt descending
    edits.sort((a, b) => b.savedAt.compareTo(a.savedAt));
    if (mounted) {
      setState(() {
        _recentEdits = edits;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    // List of screens for navigation
    final List<Widget> screens = [
      _buildHomeContent(),
      // Editor is now integrated into PlayerEditorScreen, so index 1 might be redundant or a direct jump
      // For now, let's keep placeholder or navigate directly
      Container(),
      const LibraryScreen(),
      const SettingsScreen(),
    ];

    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: Stack(
        children: [
           // Ambient Background Glows (Global)
           Positioned(
            top: -100,
            left: -100,
            child: Container(
              width: 400,
              height: 400,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Theme.of(context).primaryColor.withOpacity(0.2),
                backgroundBlendMode: BlendMode.screen,
              ),
            ),
          ),
          Positioned(
            bottom: -100,
            right: -100,
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.blue.withOpacity(0.1),
                backgroundBlendMode: BlendMode.screen,
              ),
            ),
          ),
          // Blur the glows slightly more
          BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 60, sigmaY: 60),
            child: Container(color: Colors.transparent),
          ),

           // Screen Content
           screens[_currentIndex],

           // Mini-Player Bar (Bottom) - Removed as we move to full screen editing flow
           // But user might want it if they navigate away.
           // For this specific task, "Player & Lofi Editor" implies a focused editing session.
           // We will remove the MiniPlayerBar for now to focus on the new flow.

           Positioned(
             left: 0,
             right: 0,
             bottom: 0,
             child: _buildGlassBottomNav(),
           ),
        ],
      ),
    );
  }

  Widget _buildGlassBottomNav() {
    return ClipRRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: Container(
          height: 80,
          decoration: BoxDecoration(
            color: const Color(0xFF191022).withOpacity(0.8),
            border: const Border(top: BorderSide(color: Colors.white10)),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildNavItem(Icons.home, 'Home', 0),
              // We'll hide Editor tab since it's now context-driven by selecting a song
              // _buildNavItem(Icons.tune, 'Editor', 1),
              _buildNavItem(Icons.library_music, 'Library', 2),
              _buildNavItem(Icons.settings, 'Settings', 3),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildNavItem(IconData icon, String label, int index) {
    final isSelected = _currentIndex == index;
    return GestureDetector(
      onTap: () => setState(() => _currentIndex = index),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            icon, 
            color: isSelected ? Theme.of(context).primaryColor : Colors.white54,
            size: 26,
            shadows: isSelected ? [
              Shadow(color: Theme.of(context).primaryColor, blurRadius: 10),
            ] : [],
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: GoogleFonts.splineSans(
              fontSize: 10,
              fontWeight: FontWeight.w500,
              color: isSelected ? Colors.white : Colors.white54,
            ),
          ),
        ],
      ),
    );
  }

  // Extracted Home Tab Content
  Widget _buildHomeContent() {
    return SafeArea(
      bottom: false, // Allow content to go behind bottom nav
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 20),
            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Good Evening',
                      style: GoogleFonts.splineSans(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                    Text(
                      'Ready to chill?',
                      style: GoogleFonts.splineSans(
                        fontSize: 14,
                        color: Colors.white54,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                  ],
                ),
                Container(
                  width: 40, 
                  height: 40,
                  decoration: BoxDecoration(
                    color: const Color(0xFF2D243A),
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white12),
                  ),
                  child: IconButton(
                    padding: EdgeInsets.zero,
                    icon: const Icon(Icons.settings, color: Colors.white, size: 20),
                    onPressed: () => setState(() => _currentIndex = 3), // Go to Settings
                  ),
                ),
              ],
            ),

            const SizedBox(height: 32),

            // Select Song Hero Card
            GestureDetector(
              onTap: () => _pickAudio(context),
              child: Container(
                height: 220,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: Theme.of(context).primaryColor.withOpacity(0.3)),
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      Theme.of(context).primaryColor.withOpacity(0.1),
                       const Color(0xFF231B2E).withOpacity(0.6),
                    ],
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Theme.of(context).primaryColor.withOpacity(0.15),
                      blurRadius: 30,
                      offset: const Offset(0, 10),
                    ),
                  ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(24),
                  child: BackdropFilter(
                    filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                    child: Stack(
                      children: [
                        // Inner glow
                        Positioned(
                          top: -50,
                          left: -50,
                          child: Container(
                            width: 150,
                            height: 150,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: Theme.of(context).primaryColor.withOpacity(0.2),
                              backgroundBlendMode: BlendMode.overlay,
                            ),
                          ),
                        ),
                        
                        Center(
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
                                    BoxShadow(
                                      color: Theme.of(context).primaryColor.withOpacity(0.5), 
                                      blurRadius: 20,
                                      spreadRadius: 2,
                                    ),
                                  ],
                                ),
                                child: const Icon(Icons.add, size: 40, color: Colors.white),
                              ),
                              const SizedBox(height: 20),
                              Text(
                                'Select Song',
                                style: GoogleFonts.splineSans(
                                  fontSize: 22, 
                                  fontWeight: FontWeight.bold,
                                  color: Colors.white,
                                ),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                'Tap to import audio file (MP3, WAV)',
                                style: GoogleFonts.splineSans(fontSize: 12, color: Colors.white60),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            
            const SizedBox(height: 32),
            
            // Recent Edits Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Recent Edits', 
                  style: GoogleFonts.splineSans(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
                ),
              ],
            ),
            
            const SizedBox(height: 16),
            
            // List Items
            Expanded(
              child: _recentEdits.isEmpty 
              ? Center(
                  child: Text(
                    'No recent edits',
                    style: GoogleFonts.splineSans(color: Colors.white38),
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.only(bottom: 100), // Space for bottom nav
                  itemCount: _recentEdits.length,
                  itemBuilder: (context, index) {
                    final edit = _recentEdits[index];
                    return _buildRecentItem(
                      edit: edit,
                      gradientStart: Colors.purple.shade900,
                      gradientEnd: Colors.blue.shade900,
                    );
                  },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickAudio(BuildContext context) async {
    try {
      FilePickerResult? result = await FilePicker.platform.pickFiles(
        type: FileType.audio, // Filter for Music Files only
      );

      if (result != null) {
        if (result.files.single.path != null) {
          String filePath = result.files.single.path!;
          String fileName = result.files.single.name;
          
          // Navigate to Player Editor directly
          if (mounted) {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (context) => PlayerEditorScreen(filePath: filePath, fileName: fileName),
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

  Widget _buildRecentItem({required SavedConfig edit, required Color gradientStart, required Color gradientEnd}) {
    final dateStr = DateFormat('MMM d, h:mm a').format(edit.savedAt);

    return GestureDetector(
      onTap: () {
         // Open Editor with this config
         Navigator.of(context).push(
            MaterialPageRoute(
              builder: (context) => PlayerEditorScreen(
                 filePath: edit.filePath,
                 fileName: edit.fileName,
                 savedConfig: edit,
              ),
            ),
         ).then((_) => _loadRecentEdits()); // Reload after return
      },
      child: Container(
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0xFF231B2E),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.white.withOpacity(0.05)),
        ),
        child: Row(
          children: [
            Container(
              width: 56,
              height: 56,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                gradient: LinearGradient(
                  colors: [gradientStart, gradientEnd],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
              ),
              child: const Icon(Icons.play_arrow, color: Colors.white),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    edit.fileName, 
                    style: GoogleFonts.splineSans(fontWeight: FontWeight.w600, fontSize: 16, color: Colors.white),
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      const Icon(Icons.access_time, size: 12, color: Colors.white38),
                      const SizedBox(width: 4),
                      Text(
                        dateStr, 
                        style: GoogleFonts.splineSans(color: Colors.white38, fontSize: 12),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            IconButton(
              icon: const Icon(Icons.delete_outline, color: Colors.white38),
              onPressed: () async {
                 final storage = StorageService();
                 await storage.deleteConfig(edit.id);
                 _loadRecentEdits();
              },
            ),
          ],
        ),
      ),
    );
  }
}
