import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:just_audio/just_audio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:lofiga/screens/player_editor_screen.dart'; // For Edit in Studio

class LibraryScreen extends StatefulWidget {
  const LibraryScreen({super.key});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  List<FileSystemEntity> _files = [];
  final AudioPlayer _player = AudioPlayer();
  String? _playingFilePath;
  bool _isPlaying = false;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadFiles();
    
    _player.playerStateStream.listen((state) {
      if (mounted) {
        setState(() {
          _isPlaying = state.playing;
          if (state.processingState == ProcessingState.completed) {
            _playingFilePath = null;
            _isPlaying = false;
          }
        });
      }
    });
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  Future<void> _loadFiles() async {
    setState(() => _isLoading = true);
    try {
      Directory? dir;
      if (Platform.isAndroid) {
        dir = await getExternalStorageDirectory();
      } else {
        dir = await getApplicationDocumentsDirectory();
      }

      if (dir != null) {
        final List<FileSystemEntity> files = dir.listSync()
            .where((f) => f.path.endsWith('.mp3') || f.path.endsWith('.m4a'))
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

  Future<void> _playFile(String path) async {
    try {
      if (_playingFilePath == path && _isPlaying) {
        await _player.pause();
      } else {
        if (_playingFilePath != path) {
           await _player.setFilePath(path);
        }
        await _player.play();
        setState(() => _playingFilePath = path);
      }
    } catch (e) {
      debugPrint('Error playing file: $e');
      if (mounted) {
         ScaffoldMessenger.of(context).showSnackBar(
           SnackBar(content: Text('Error playing file: $e')),
         );
      }
    }
  }

  Future<void> _deleteFile(FileSystemEntity file) async {
    try {
      if (_playingFilePath == file.path) {
        await _player.stop();
        setState(() => _playingFilePath = null);
      }
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
                        final isPlaying = _playingFilePath == file.path && _isPlaying;
                        final fileName = file.uri.pathSegments.last;
                        
                        return Container(
                          margin: const EdgeInsets.only(bottom: 12),
                          decoration: BoxDecoration(
                            color: const Color(0xFF231B2E),
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(
                               color: isPlaying 
                                 ? const Color(0xFF993DF5).withOpacity(0.5)
                                 : Colors.white.withOpacity(0.05)
                            ),
                          ),
                          child: ListTile(
                            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                            leading: GestureDetector(
                              onTap: () => _playFile(file.path),
                              child: Container(
                                width: 48, 
                                height: 48,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: isPlaying ? const Color(0xFF993DF5) : Colors.white10,
                                ),
                                child: Icon(
                                  isPlaying ? Icons.pause : Icons.play_arrow, 
                                  color: Colors.white
                                ),
                              ),
                            ),
                            title: Text(
                              fileName, 
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: GoogleFonts.splineSans(
                                color: isPlaying ? const Color(0xFF993DF5) : Colors.white,
                                fontWeight: FontWeight.w600
                              ),
                            ),
                            subtitle: Text(
                              'Tap to play',
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
                                } else if (value == 'delete') {
                                   _deleteFile(file);
                                }
                              },
                              itemBuilder: (context) => [
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
