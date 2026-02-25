import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:just_audio/just_audio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:lofiga/screens/player_editor_screen.dart'; // For Edit in Studio
import 'package:lofiga/screens/exported_player_screen.dart'; // For Fullscreen Player
import 'package:share_plus/share_plus.dart';
import 'package:lofiga/services/storage_service.dart';

class LibraryScreen extends StatefulWidget {
  const LibraryScreen({super.key});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  List<FileSystemEntity> _files = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadFiles();
  }

  Future<void> _loadFiles() async {
    setState(() => _isLoading = true);
    try {
      final settings = await StorageService().loadAppSettings();
      String searchPath = settings.exportPath;

      Directory? dir;
      if (searchPath.isNotEmpty) {
        dir = Directory(searchPath);
      } else {
        if (Platform.isAndroid) {
          dir = await getExternalStorageDirectory();
        } else {
          dir = await getApplicationDocumentsDirectory();
        }
      }

      if (dir != null && await dir.exists()) {
        final List<FileSystemEntity> files = dir.listSync()
            .where((f) => f.path.endsWith('.mp3') || f.path.endsWith('.m4a') || f.path.endsWith('.wav') || f.path.endsWith('.aac'))
            .toList();
            
        // Sort by modified date descending
        files.sort((a, b) => b.statSync().modified.compareTo(a.statSync().modified));
        
        if (mounted) {
          setState(() {
            _files = files;
            _isLoading = false;
          });
        }
      }
    } catch (e) {
      debugPrint('Error loading files: $e');
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _deleteFile(FileSystemEntity file) async {
    try {
      await file.delete();
      _loadFiles();
      if (mounted) {
         ScaffoldMessenger.of(context).showSnackBar(
           const SnackBar(content: Text('File deleted')),
         );
      }
    } catch (e) {
      debugPrint('Error deleting file: $e');
    }
  }

  void _shareFile(String path) async {
    final text = 'Listen to my new chill Lofi track generated with Lofiga! 🎧✨ Download the app to make your own vibes: [App Link]';
    await Share.shareXFiles(
      [XFile(path)],
      text: text,
      subject: 'My Lofiga Track',
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent, // Inherits from HomeScreen stack
      body: SafeArea(
        child: Column(
          children: [
             Padding(
               padding: const EdgeInsets.all(24.0),
               child: Row(
                 children: [
                   Text(
                     'Your Mixes',
                     style: GoogleFonts.splineSans(
                       fontSize: 28,
                       fontWeight: FontWeight.bold,
                       color: Colors.white,
                     ),
                   ),
                 ],
               ),
             ),
             
             Expanded(
               child: _isLoading 
               ? const Center(child: CircularProgressIndicator(color: Color(0xFF993DF5)))
               : _files.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.music_note, size: 64, color: Colors.white.withOpacity(0.2)),
                          const SizedBox(height: 16),
                          Text(
                            'No generated songs yet',
                            style: GoogleFonts.splineSans(color: Colors.white54),
                          ),
                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
                      itemCount: _files.length,
                      itemBuilder: (context, index) {
                        final file = _files[index];
                        final fileName = file.uri.pathSegments.last;
                        
                        return GestureDetector(
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => ExportedPlayerScreen(
                                  filePath: file.path,
                                  fileName: fileName,
                                )
                              )
                            ).then((_) => _loadFiles()); // Refresh on back
                          },
                          child: Container(
                            margin: const EdgeInsets.only(bottom: 12),
                            decoration: BoxDecoration(
                              color: const Color(0xFF231B2E),
                              borderRadius: BorderRadius.circular(16),
                              border: Border.all(
                                 color: Colors.white.withOpacity(0.05)
                              ),
                            ),
                            child: ListTile(
                              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                              leading: Container(
                                width: 48, 
                                height: 48,
                                decoration: const BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: Colors.white10,
                                ),
                                child: const Icon(
                                  Icons.play_arrow, 
                                  color: Colors.white
                                ),
                              ),
                              title: Text(
                                fileName, 
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: GoogleFonts.splineSans(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w600
                                ),
                              ),
                              subtitle: Text(
                                'Tap to open player',
                                style: GoogleFonts.splineSans(color: Colors.white38, fontSize: 12),
                              ),
                              trailing: PopupMenuButton<String>(
                                icon: const Icon(Icons.more_vert, color: Colors.white38),
                                color: const Color(0xFF2A1F36),
                                onSelected: (value) {
                                  if (value == 'edit') {
                                     Navigator.push(
                                       context, 
                                       MaterialPageRoute(
                                         builder: (context) => PlayerEditorScreen(
                                            filePath: file.path, 
                                            fileName: fileName
                                         )
                                       )
                                     );
                                  } else if (value == 'share') {
                                     _shareFile(file.path);
                                  } else if (value == 'delete') {
                                     _deleteFile(file);
                                  }
                                },
                                itemBuilder: (context) => [
                                  PopupMenuItem(
                                    value: 'share',
                                    child: Row(
                                      children: [
                                        const Icon(Icons.share, color: Colors.white, size: 18),
                                        const SizedBox(width: 8),
                                        Text('Share', style: GoogleFonts.splineSans(color: Colors.white)),
                                      ],
                                    ),
                                  ),
                                  PopupMenuItem(
                                    value: 'edit',
                                    child: Row(
                                      children: [
                                        const Icon(Icons.tune, color: Colors.white, size: 18),
                                        const SizedBox(width: 8),
                                        Text('Edit in Studio', style: GoogleFonts.splineSans(color: Colors.white)),
                                      ],
                                    ),
                                  ),
                                  PopupMenuItem(
                                    value: 'delete',
                                    child: Row(
                                      children: [
                                        const Icon(Icons.delete, color: Colors.redAccent, size: 18),
                                        const SizedBox(width: 8),
                                        Text('Delete', style: GoogleFonts.splineSans(color: Colors.redAccent)),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        );
                      },
                    ),
             ),
          ],
        ),
      ),
    );
  }
}
