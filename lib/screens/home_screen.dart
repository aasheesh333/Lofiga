import 'dart:io';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:file_picker/file_picker.dart';
import 'package:provider/provider.dart';
import 'package:lofiga/logic/preset_manager.dart';
import 'package:lofiga/screens/player_editor_screen.dart';
import 'package:lofiga/screens/library_screen.dart';
import 'package:lofiga/screens/settings_screen.dart';
import 'package:lofiga/services/storage_service.dart';
import 'package:intl/intl.dart'; 
import 'dart:ui'; // For BackdropFilter
import 'package:on_audio_query/on_audio_query.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:path_provider/path_provider.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with WidgetsBindingObserver {
  int _currentIndex = 0;
  List<SavedConfig> _recentEdits = [];
  
  // All Songs Logic
  final OnAudioQuery _audioQuery = OnAudioQuery();
  List<SongModel> _allSongs = [];
  List<SongModel> _filteredSongs = [];
  bool _hasPermission = false;
  bool _isLoadingSongs = true;
  final TextEditingController _searchController = TextEditingController();
  bool _isSearching = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _loadRecentEdits();
    _checkPermissionAndLoadSongs();
    _searchController.addListener(_onSearchChanged);
  }
  
  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _searchController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      // Reload songs when app comes to foreground (e.g. after downloading a file)
      if (_hasPermission) {
        _loadSongs();
      }
    }
  }

  void _onSearchChanged() {
    final query = _searchController.text.toLowerCase();
    setState(() {
      if (query.isEmpty) {
        _filteredSongs = _allSongs;
        _isSearching = false;
      } else {
        _isSearching = true;
        _filteredSongs = _allSongs.where((song) {
          return song.title.toLowerCase().contains(query) || 
                 (song.artist?.toLowerCase().contains(query) ?? false);
        }).toList();
      }
    });
  }

  Future<void> _loadRecentEdits() async {
    final storage = StorageService();
    final edits = await storage.loadAllConfigs();
    edits.sort((a, b) => b.savedAt.compareTo(a.savedAt));
    
    // Bug #9 fix: Filter out configs whose files no longer exist
    final validEdits = <SavedConfig>[];
    for (final edit in edits) {
      if (await File(edit.filePath).exists()) {
        validEdits.add(edit);
      }
    }
    
    if (mounted) {
      setState(() {
        _recentEdits = validEdits;
      });
    }
  }
  
  // Bug #8 fix: Completely rewritten permission handling
  Future<void> _checkPermissionAndLoadSongs() async {
    setState(() => _isLoadingSongs = true);
    
    bool permissionStatus = false;
    
    if (Platform.isAndroid) {
      // Android 13+ (API 33+): Use READ_MEDIA_AUDIO
      // Android 10-12 (API 29-32): Use READ_EXTERNAL_STORAGE
      // Android <10 (API <29): Use READ_EXTERNAL_STORAGE + WRITE_EXTERNAL_STORAGE
      
      final androidInfo = await _getAndroidSdkVersion();
      
      if (androidInfo >= 33) {
        // Android 13+: Only READ_MEDIA_AUDIO matters
        var status = await Permission.audio.status;
        if (status.isGranted) {
          permissionStatus = true;
        } else if (status.isDenied) {
          status = await Permission.audio.request();
          permissionStatus = status.isGranted;
        } else if (status.isPermanentlyDenied) {
          // User previously denied and checked "Don't ask again"
          if (mounted) {
            _showPermissionDeniedDialog();
          }
        }
      } else {
        // Android 10-12: Use storage permission
        var status = await Permission.storage.status;
        if (status.isGranted) {
          permissionStatus = true;
        } else if (status.isDenied) {
          status = await Permission.storage.request();
          permissionStatus = status.isGranted;
        } else if (status.isPermanentlyDenied) {
          if (mounted) {
            _showPermissionDeniedDialog();
          }
        }
      }
    } else {
      // iOS
      permissionStatus = await _audioQuery.permissionsStatus();
      if (!permissionStatus) {
        permissionStatus = await _audioQuery.permissionsRequest();
      }
    }
    
    if (mounted) {
      setState(() {
        _hasPermission = permissionStatus;
        _isLoadingSongs = false;
      });
    }
    
    if (permissionStatus) {
      _loadSongs();
    }
  }

  // Helper to get Android SDK version
  Future<int> _getAndroidSdkVersion() async {
    try {
      // on_audio_query's DeviceModel doesn't expose SDK, using a simpler approach
      // For Android 13+, Permission.audio exists. For older, it doesn't.
      // We can check by trying the audio permission — if it's not applicable,
      // it will return a specific status.
      final audioStatus = await Permission.audio.status;
      // If audio permission is not applicable (old Android), it returns granted
      // On Android 13+, it returns denied/granted based on actual state
      // Heuristic: try both and see which one is meaningful
      if (audioStatus != PermissionStatus.granted) {
        // Likely Android 13+ where audio perm is needed
        return 33;
      }
      // Check if storage is also granted
      final storageStatus = await Permission.storage.status;
      if (storageStatus.isGranted) {
        return 29; // Assume older Android with storage granted
      }
      return 33; // Default to treating as Android 13+
    } catch (e) {
      return 33; // Default
    }
  }

  void _showPermissionDeniedDialog() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF2A1F36),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(
          'Permission Required',
          style: GoogleFonts.splineSans(color: Colors.white, fontWeight: FontWeight.bold),
        ),
        content: Text(
          'Audio permission is permanently denied. Please enable it from Settings to see your songs.',
          style: GoogleFonts.splineSans(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text('Cancel', style: GoogleFonts.splineSans(color: Colors.white54)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF993DF5),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            onPressed: () {
              Navigator.pop(ctx);
              openAppSettings(); // Opens OS app settings
            },
            child: Text('Open Settings', style: GoogleFonts.splineSans(color: Colors.white)),
          ),
        ],
      ),
    );
  }
  
  Future<void> _loadSongs() async {
    try {
      final songs = await _audioQuery.querySongs(
        sortType: SongSortType.DATE_ADDED,
        orderType: OrderType.DESC_OR_GREATER,
        uriType: UriType.EXTERNAL,
        ignoreCase: true,
      );
      
      // Filter out atmosphere files and short clips
      final filteredList = songs.where((song) {
        final title = song.title.toLowerCase();
        bool isAtmosphere = title.contains('rain_') || 
                            title.contains('wind_') || 
                            title.contains('vinyl_') || 
                            title.contains('tape_loop');
                            
        bool isShort = (song.duration ?? 0) < 10000; // 10 seconds
        
        return !isAtmosphere && !isShort;
      }).toList();
      
      if (mounted) {
        setState(() {
          _allSongs = filteredList;
          if (_searchController.text.isNotEmpty) {
             _onSearchChanged();
          } else {
             _filteredSongs = filteredList;
          }
        });
      }
    } catch (e) {
      debugPrint("Error loading songs: $e");
    }
  }

  // Bug #9 fix: Copy picked file to app's persistent directory so path survives cache clears
  Future<String> _persistPickedFile(String cachedPath, String fileName) async {
    try {
      final appDir = await getApplicationDocumentsDirectory();
      final musicDir = Directory('${appDir.path}/picked_music');
      if (!await musicDir.exists()) {
        await musicDir.create(recursive: true);
      }
      
      final destPath = '${musicDir.path}/$fileName';
      final destFile = File(destPath);
      
      // Only copy if not already persisted
      if (!await destFile.exists()) {
        await File(cachedPath).copy(destPath);
      }
      
      return destPath;
    } catch (e) {
      debugPrint('Failed to persist picked file, using original path: $e');
      return cachedPath; // Fallback to original path
    }
  }

  @override
  Widget build(BuildContext context) {
    // List of screens for navigation
    final List<Widget> screens = [
      _buildHomeContent(),
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
          BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 60, sigmaY: 60),
            child: Container(color: Colors.transparent),
          ),

           // Screen Content
           screens[_currentIndex],

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
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 24.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const SizedBox(height: 20),
            
            // Search Bar & Header
            Row(
              children: [
                Expanded(
                  child: Container(
                    height: 50,
                    decoration: BoxDecoration(
                      color: const Color(0xFF2A1F36),
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: Colors.white12),
                    ),
                    child: TextField(
                      controller: _searchController,
                      style: GoogleFonts.splineSans(color: Colors.white),
                      decoration: InputDecoration(
                        hintText: 'Search songs...',
                        hintStyle: GoogleFonts.splineSans(color: Colors.white38),
                        prefixIcon: const Icon(Icons.search, color: Colors.white38),
                        border: InputBorder.none,
                        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Container(
                  width: 50, 
                  height: 50,
                  decoration: BoxDecoration(
                    color: const Color(0xFF2D243A),
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white12),
                  ),
                  child: IconButton(
                    padding: EdgeInsets.zero,
                    icon: const Icon(Icons.settings, color: Colors.white, size: 24),
                    onPressed: () => setState(() => _currentIndex = 3), 
                  ),
                ),
              ],
            ),

            const SizedBox(height: 24),

            // File Picker & All Songs Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  _isSearching ? 'Search Results' : 'All Songs', 
                  style: GoogleFonts.splineSans(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
                ),
                if (!_isSearching)
                  TextButton.icon(
                    onPressed: () => _pickAudio(context),
                    icon: const Icon(Icons.folder_open, size: 16, color: Colors.white54),
                    label: Text('Open Files App', style: GoogleFonts.splineSans(color: Colors.white54)),
                  ),
              ],
            ),
            const SizedBox(height: 12),

            // Use Expanded properly to allow list to scroll
            Expanded(
              child: RefreshIndicator(
                onRefresh: () async {
                  await _loadRecentEdits();
                  if (_hasPermission) await _loadSongs();
                },
                color: Theme.of(context).primaryColor,
                backgroundColor: const Color(0xFF2A1F36),
                child: ListView(
                  padding: const EdgeInsets.only(bottom: 100),
                  children: [
                    // Recent Edits (Horizontal) - Hide if searching
                    if (!_isSearching && _recentEdits.isNotEmpty) ...[
                       Text(
                        'Recent Projects', 
                        style: GoogleFonts.splineSans(fontSize: 18, fontWeight: FontWeight.bold, color: Colors.white),
                      ),
                      const SizedBox(height: 12),
                      SizedBox(
                        height: 140,
                        child: ListView.builder(
                          scrollDirection: Axis.horizontal,
                          itemCount: _recentEdits.length,
                          itemBuilder: (context, index) {
                             final edit = _recentEdits[index];
                             final dateStr = DateFormat('MMM d').format(edit.savedAt);
                             return GestureDetector(
                               onTap: () {
                                 Navigator.of(context).push(
                                    MaterialPageRoute(
                                      builder: (context) => PlayerEditorScreen(
                                         filePath: edit.filePath,
                                         fileName: edit.fileName,
                                         savedConfig: edit,
                                      ),
                                    ),
                                 ).then((_) => _loadRecentEdits());
                               },
                               child: Container(
                                 width: 120,
                                 margin: const EdgeInsets.only(right: 12),
                                 padding: const EdgeInsets.all(12),
                                 decoration: BoxDecoration(
                                   color: const Color(0xFF231B2E),
                                   borderRadius: BorderRadius.circular(16),
                                   border: Border.all(color: Colors.white.withOpacity(0.05)),
                                 ),
                                 child: Column(
                                   crossAxisAlignment: CrossAxisAlignment.start,
                                   mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                   children: [
                                     Container(
                                       width: 40, height: 40,
                                       alignment: Alignment.center,
                                       decoration: BoxDecoration(
                                         color: Theme.of(context).primaryColor.withOpacity(0.2),
                                         shape: BoxShape.circle,
                                       ),
                                       child: const Icon(Icons.history, color: Colors.white70),
                                     ),
                                     Column(
                                       crossAxisAlignment: CrossAxisAlignment.start,
                                       children: [
                                         Text(
                                           edit.fileName,
                                           maxLines: 2,
                                           overflow: TextOverflow.ellipsis,
                                           style: GoogleFonts.splineSans(fontWeight: FontWeight.bold, color: Colors.white, fontSize: 13),
                                         ),
                                         const SizedBox(height: 4),
                                         Text(
                                           dateStr,
                                           style: GoogleFonts.splineSans(color: Colors.white38, fontSize: 11),
                                         ),
                                       ],
                                     ),
                                   ],
                                 ),
                               ),
                             );
                          },
                        ),
                      ),
                      const SizedBox(height: 24),
                    ],

                    if (!_hasPermission)
                      Container(
                        padding: const EdgeInsets.all(20),
                        decoration: BoxDecoration(
                          color: Colors.orange.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: Colors.orange.withOpacity(0.3)),
                        ),
                        child: Column(
                          children: [
                            const Icon(Icons.folder_off, size: 40, color: Colors.orange),
                            const SizedBox(height: 8),
                            Text(
                              'Permission Required',
                              style: GoogleFonts.splineSans(color: Colors.orange, fontWeight: FontWeight.bold),
                            ),
                            Text(
                              'Grant access to load all system songs.',
                              textAlign: TextAlign.center,
                              style: GoogleFonts.splineSans(color: Colors.white54, fontSize: 12),
                            ),
                            const SizedBox(height: 12),
                            ElevatedButton(
                              style: ElevatedButton.styleFrom(backgroundColor: Colors.orange),
                              onPressed: () async {
                                // Bug #8 fix: Try requesting permission again, or open settings if permanently denied
                                await _checkPermissionAndLoadSongs();
                                if (!_hasPermission && mounted) {
                                  // If still no permission, open app settings
                                  _showPermissionDeniedDialog();
                                }
                              },
                              child: const Text('Allow Access'),
                            ),
                          ],
                        ),
                      )
                    else if (_isLoadingSongs)
                      const Padding(
                        padding: EdgeInsets.all(32),
                        child: Center(child: CircularProgressIndicator(color: Color(0xFF993DF5))),
                      )
                    else if (_filteredSongs.isEmpty)
                       Padding(
                         padding: const EdgeInsets.all(32),
                         child: Center(child: Text('No songs found', style: GoogleFonts.splineSans(color: Colors.white38))),
                       )
                    else
                      ..._filteredSongs.map((song) => _buildSongItem(song)),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _pickAudio(BuildContext context) async {
    try {
      FilePickerResult? result;
      if (Platform.isAndroid) {
        result = await FilePicker.platform.pickFiles(
          type: FileType.audio,
        );
      } else {
        result = await FilePicker.platform.pickFiles(
          type: FileType.custom, 
          allowedExtensions: ['mp3', 'wav', 'm4a', 'aac', 'flac', 'ogg'],
        );
      }

      if (result != null) {
        if (result.files.single.path != null) {
          String filePath = result.files.single.path!;
          String fileName = result.files.single.name;
          
          // Bug #9 fix: On Android, FilePicker returns cached paths that get cleared.
          // Persist the file to app's documents directory for reliable access.
          if (Platform.isAndroid) {
            filePath = await _persistPickedFile(filePath, fileName);
          }
          
          // Navigate to Player Editor directly
          if (mounted) {
            Navigator.of(context).push(
              MaterialPageRoute(
                builder: (context) => PlayerEditorScreen(filePath: filePath, fileName: fileName),
              ),
            ).then((_) => _loadRecentEdits());
          }
        } else {
          if (context.mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Error: Selected file path is null')),
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

  Widget _buildSongItem(SongModel song) {
    return GestureDetector(
      onTap: () {
        Navigator.of(context).push(
           MaterialPageRoute(
             builder: (context) => PlayerEditorScreen(
                filePath: song.data,
                fileName: song.title,
             ),
           ),
        ).then((_) => _loadRecentEdits());
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
              width: 50,
              height: 50,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                color: const Color(0xFF352B42),
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: QueryArtworkWidget(
                  id: song.id,
                  type: ArtworkType.AUDIO,
                  nullArtworkWidget: const Icon(Icons.music_note, color: Colors.white38),
                  errorBuilder: (context, exception, stackTrace) {
                    return const Icon(Icons.music_note, color: Colors.white38);
                  },
                ),
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    song.title, 
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: GoogleFonts.splineSans(fontWeight: FontWeight.w600, fontSize: 15, color: Colors.white),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    song.artist ?? 'Unknown Artist', 
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: GoogleFonts.splineSans(color: Colors.white38, fontSize: 12),
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: Colors.white24),
          ],
        ),
      ),
    );
  }
}
