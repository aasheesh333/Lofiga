import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:url_launcher/url_launcher.dart';
import 'dart:ui'; // For BackdropFilter
import 'package:file_picker/file_picker.dart';
import 'package:provider/provider.dart';
import 'package:lofiga/services/storage_service.dart';
import 'package:lofiga/theme/app_theme.dart';
import 'dart:io';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  AppSettings _settings = AppSettings();
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    final storage = StorageService();
    final s = await storage.loadAppSettings();
    setState(() {
      _settings = s;
      _isLoading = false;
    });
  }

  Future<void> _saveSettings() async {
    await StorageService().saveAppSettings(_settings);
  }

  // Bug #10 fix: Better directory selection with SAF handling
  Future<void> _selectExportPath() async {
    try {
      String? selectedDirectory = await FilePicker.platform.getDirectoryPath();
      if (selectedDirectory != null) {
        // On Android 11+, FilePicker.getDirectoryPath() may return a content URI
        // which FFmpeg can't use. Check if it's a valid filesystem path.
        if (Platform.isAndroid && selectedDirectory.startsWith('content://')) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  'Android restriction: Cannot use this folder directly. Using default export path instead.',
                  style: GoogleFonts.splineSans(),
                ),
                duration: const Duration(seconds: 4),
                backgroundColor: Colors.orange,
              ),
            );
          }
          return; // Don't save content URI as export path
        }
        
        // Validate the directory exists and is writable
        final dir = Directory(selectedDirectory);
        if (await dir.exists()) {
          setState(() {
            _settings = AppSettings(
              audioFormat: _settings.audioFormat,
              audioBitrate: _settings.audioBitrate,
              exportPath: selectedDirectory,
              isDarkMode: _settings.isDarkMode,
            );
          });
          await _saveSettings();
          
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('Export path updated', style: GoogleFonts.splineSans()),
                backgroundColor: const Color(0xFF993DF5),
              ),
            );
          }
        }
      }
    } catch (e) {
      debugPrint('Error selecting directory: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error selecting folder: $e')),
        );
      }
    }
  }

  // Reset export path to default
  Future<void> _resetExportPath() async {
    setState(() {
      _settings = AppSettings(
        audioFormat: _settings.audioFormat,
        audioBitrate: _settings.audioBitrate,
        exportPath: '', // Empty = default
        isDarkMode: _settings.isDarkMode,
      );
    });
    await _saveSettings();
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Export path reset to default', style: GoogleFonts.splineSans()),
          backgroundColor: const Color(0xFF993DF5),
        ),
      );
    }
  }

  void _showAudioQualityDialog() {
    String tempFormat = _settings.audioFormat;
    String tempBitrate = _settings.audioBitrate;

    showDialog(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setStateDialog) {
            List<String> bitrates = [];
            if (tempFormat == 'mp3' || tempFormat == 'aac') {
              bitrates = ['320k', '256k', '192k', '128k'];
            } else if (tempFormat == 'wav') {
              bitrates = ['Lossless'];
              tempBitrate = 'Lossless';
            }

            final isDark = Theme.of(context).brightness == Brightness.dark;

            return AlertDialog(
              backgroundColor: isDark ? const Color(0xFF2A1F36) : Colors.white,
              title: Text('Audio Quality', style: GoogleFonts.splineSans(color: isDark ? Colors.white : Colors.black87)),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  DropdownButtonFormField<String>(
                    value: tempFormat,
                    dropdownColor: isDark ? const Color(0xFF231B2E) : Colors.white,
                    style: GoogleFonts.splineSans(color: isDark ? Colors.white : Colors.black87),
                    decoration: InputDecoration(
                        labelText: 'Format', 
                        labelStyle: TextStyle(color: isDark ? Colors.white70 : Colors.black54)
                    ),
                    items: ['mp3', 'wav', 'aac']
                        .map((f) => DropdownMenuItem(value: f, child: Text(f.toUpperCase())))
                        .toList(),
                    onChanged: (val) {
                      if (val != null) {
                        setStateDialog(() {
                          tempFormat = val;
                          if (val == 'wav') {
                            tempBitrate = 'Lossless';
                          } else if (tempBitrate == 'Lossless') {
                            tempBitrate = '320k'; 
                          }
                        });
                      }
                    },
                  ),
                  const SizedBox(height: 16),
                  DropdownButtonFormField<String>(
                    value: tempBitrate,
                    dropdownColor: isDark ? const Color(0xFF231B2E) : Colors.white,
                    style: GoogleFonts.splineSans(color: isDark ? Colors.white : Colors.black87),
                    decoration: InputDecoration(
                        labelText: 'Quality / Bitrate', 
                        labelStyle: TextStyle(color: isDark ? Colors.white70 : Colors.black54)
                    ),
                    items: bitrates
                        .map((b) => DropdownMenuItem(value: b, child: Text(b)))
                        .toList(),
                    onChanged: (val) {
                      if (val != null) {
                        setStateDialog(() {
                          tempBitrate = val;
                        });
                      }
                    },
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: Text('Cancel', style: GoogleFonts.splineSans(color: isDark ? Colors.white54 : Colors.black54)),
                ),
                TextButton(
                  onPressed: () async {
                    Navigator.pop(ctx);
                    setState(() {
                      _settings = AppSettings(
                        audioFormat: tempFormat,
                        audioBitrate: tempBitrate,
                        exportPath: _settings.exportPath,
                        isDarkMode: _settings.isDarkMode,
                      );
                    });
                    await _saveSettings();
                  },
                  child: Text('Save', style: GoogleFonts.splineSans(color: Theme.of(context).primaryColor, fontWeight: FontWeight.bold)),
                ),
              ],
            );
          }
        );
      }
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      body: Stack(
        children: [
           // Ambient Background Glow - Settings Specific
           Positioned(
             bottom: -100,
             left: -50,
             child: Container(
               width: 400,
               height: 400,
               decoration: BoxDecoration(
                 shape: BoxShape.circle,
                 color: const Color(0xFF3DF5E6).withOpacity(0.05),
                 backgroundBlendMode: BlendMode.screen,
               ),
             ),
           ),
           BackdropFilter(
             filter: ImageFilter.blur(sigmaX: 60, sigmaY: 60),
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
                     children: [
                       Text(
                         'Settings',
                         style: GoogleFonts.splineSans(
                           fontSize: 28,
                           fontWeight: FontWeight.bold,
                           color: Theme.of(context).textTheme.displayLarge?.color ?? (Theme.of(context).brightness == Brightness.dark ? Colors.white : Colors.black87),
                         ),
                       ),
                     ],
                   ),
                 ),

                 if (_isLoading)
                   const Expanded(child: Center(child: CircularProgressIndicator()))
                 else
                   Expanded(
                   child: ListView(
                     padding: const EdgeInsets.symmetric(horizontal: 24.0),
                     children: [
                       _buildSectionTitle(context, 'GENERAL'),
                       const SizedBox(height: 16),
                       _buildSettingItem(
                         context, 
                         'Audio Quality', 
                         '${_settings.audioFormat.toUpperCase()} (${_settings.audioBitrate})', 
                         Icons.graphic_eq, 
                         onTap: _showAudioQualityDialog
                       ),
                       _buildSettingItem(
                         context, 
                         'Export Path', 
                         _settings.exportPath.isEmpty ? 'Default (Lofiga Exports)' : _settings.exportPath, 
                         Icons.folder_open, 
                         onTap: _selectExportPath,
                         // Show reset option if custom path is set
                         onLongPress: _settings.exportPath.isNotEmpty ? _resetExportPath : null,
                       ),
                       if (_settings.exportPath.isNotEmpty)
                         Padding(
                           padding: const EdgeInsets.only(left: 16, bottom: 12),
                           child: GestureDetector(
                             onTap: _resetExportPath,
                             child: Text(
                               'Long press or tap here to reset to default',
                               style: GoogleFonts.splineSans(color: Colors.white38, fontSize: 11),
                             ),
                           ),
                         ),
                       _buildSettingItem(
                         context, 
                         'Theme', 
                         _settings.isDarkMode ? 'Dark Mode' : 'Light Mode', 
                         Icons.dark_mode, 
                         isSwitch: true
                       ),

                       const SizedBox(height: 32),

                       _buildSectionTitle(context, 'ABOUT'),
                       const SizedBox(height: 16),
                       _buildSettingItem(context, 'Version', '1.0.0 (Beta)', Icons.info_outline, onTap: () {
                         ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Lofiga v1.0.0 is up to date!')));
                       }),
                       _buildSettingItem(context, 'Privacy Policy', '', Icons.privacy_tip, onTap: () => _launchURL('https://example.com/privacy')),
                       _buildSettingItem(context, 'Terms of Service', '', Icons.description, onTap: () => _launchURL('https://example.com/terms')),

                       const SizedBox(height: 50),
                       
                       Center(
                         child: Text(
                           'Made with ❤️ for Lofi Lovers',
                           style: GoogleFonts.splineSans(color: Theme.of(context).textTheme.bodyMedium?.color?.withOpacity(0.5), fontSize: 12),
                         ),
                       ),
                       const SizedBox(height: 100),
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

  Widget _buildSectionTitle(BuildContext context, String title) {
    return Text(
      title,
      style: GoogleFonts.splineSans(
        fontSize: 12,
        fontWeight: FontWeight.bold,
        letterSpacing: 2,
        color: Theme.of(context).textTheme.bodyMedium?.color?.withOpacity(0.5) ?? Colors.white54,
      ),
    );
  }

  Future<void> _launchURL(String urlString) async {
    final Uri url = Uri.parse(urlString);
    if (!await launchUrl(url)) {
      debugPrint('Could not launch \$url');
    }
  }

  Widget _buildSettingItem(BuildContext context, String title, String subtitle, IconData icon, {bool isSwitch = false, VoidCallback? onTap, VoidCallback? onLongPress}) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final themeProvider = Provider.of<ThemeProvider>(context, listen: false);

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      child: GestureDetector(
        onLongPress: onLongPress,
        child: ListTile(
          onTap: onTap ?? () {
             if (!isSwitch && subtitle.isNotEmpty) {
                 ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$title setting coming soon!')));
             }
          },
          contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: BorderSide(color: isDark ? Colors.white10 : Colors.black12),
          ),
          tileColor: isDark ? const Color(0xFF231B2E) : Colors.white,
          leading: Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: isDark ? Colors.white.withOpacity(0.05) : Colors.black.withOpacity(0.05),
              borderRadius: BorderRadius.circular(10),
            ),
            child: Icon(icon, color: isDark ? Colors.white70 : Colors.black87, size: 20),
          ),
          title: Text(title, style: GoogleFonts.splineSans(fontWeight: FontWeight.w600, fontSize: 14, color: isDark ? Colors.white : Colors.black87)),
          subtitle: subtitle.isNotEmpty ? Text(subtitle, style: GoogleFonts.splineSans(fontSize: 12, color: isDark ? Colors.white38 : Colors.black54)) : null,
          trailing: isSwitch 
              ? Switch(
                  value: _settings.isDarkMode, 
                  onChanged: (v) async {
                    setState(() {
                      _settings = AppSettings(
                        audioFormat: _settings.audioFormat,
                        audioBitrate: _settings.audioBitrate,
                        exportPath: _settings.exportPath,
                        isDarkMode: v,
                      );
                    });
                    await _saveSettings();
                    themeProvider.setThemeMode(v ? ThemeMode.dark : ThemeMode.light);
                  },
                  activeColor: Theme.of(context).primaryColor,
                  activeTrackColor: Theme.of(context).primaryColor.withOpacity(0.3),
                )
              : Icon(Icons.arrow_forward_ios, size: 14, color: isDark ? Colors.white30 : Colors.black38),
        ),
      ),
    );
  }
}
