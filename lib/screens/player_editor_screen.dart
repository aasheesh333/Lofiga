import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:provider/provider.dart';
import 'package:lofiga/logic/preset_manager.dart';
import 'package:lofiga/logic/audio_engine.dart';
import 'package:lofiga/logic/export_service.dart';
import 'package:lofiga/services/storage_service.dart';
import 'dart:async';
import 'dart:ui';
import 'dart:math' as math;

class PlayerEditorScreen extends StatefulWidget {
  final String filePath;
  final String fileName;
  final SavedConfig? savedConfig; // Optional config to restore

  const PlayerEditorScreen({
    super.key,
    required this.filePath,
    required this.fileName,
    this.savedConfig,
  });

  @override
  State<PlayerEditorScreen> createState() => _PlayerEditorScreenState();
}

class _PlayerEditorScreenState extends State<PlayerEditorScreen> with SingleTickerProviderStateMixin {
  late AudioEngine _engine;
  late PresetManager _presetManager;
  bool _isExporting = false;
  late AnimationController _waveController;
  Timer? _autoSaveTimer;
  late VoidCallback _presetListener;

  @override
  void initState() {
    super.initState();
    _engine = AudioEngine();
    _presetManager = context.read<PresetManager>();

    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _engine.init();
      await _engine.loadTrack(widget.filePath);
      
      if (mounted) {
        if (widget.savedConfig != null) {
           // Restore Effects
           _restoreConfig(widget.savedConfig!);
        } else {
           _presetManager.applyPreset(LofiPreset.lofiSlow); // Default
        }

        // Auto-save listener
        _presetListener = () {
          if (_autoSaveTimer?.isActive ?? false) _autoSaveTimer!.cancel();
          _autoSaveTimer = Timer(const Duration(seconds: 1), () {
            if (mounted) _handleSave(silent: true);
          });
        };
        _presetManager.addListener(_presetListener);
        // Trigger initial save so it appears in recent immediately
        _handleSave(silent: true);
      }
    });

    _waveController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat();
  }

  @override
  void dispose() {
    _autoSaveTimer?.cancel();
    _presetManager.removeListener(_presetListener);
    _waveController.dispose();
    _engine.stop();
    super.dispose();
  }

  Future<void> _handleExport() async {
    setState(() {
      _isExporting = true;
    });

    final preset = context.read<PresetManager>();
    final path = await ExportService.exportTrack(
      inputPath: widget.filePath,
      preset: preset,
      onProgress: (p) {},
    );

    if (mounted) {
      setState(() => _isExporting = false);
      if (path != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Exported to: $path'),
            backgroundColor: Theme.of(context).primaryColor,
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Export Cancelled or Failed')),
        );
      }
    }
  }

  Future<void> _cancelExport() async {
    await ExportService.cancelExport();
    if (mounted) {
      setState(() => _isExporting = false);
    }
  }

  void _handleMenuSelection(String value) {
    switch (value) {
      case 'export':
        _handleExport();
        break;
      case 'share':
        _handleShare();
        break;
      case 'save':
        _handleSave();
        break;
      case 'create_preset':
        _handleCreatePreset();
        break;
    }
  }

  void _restoreConfig(SavedConfig config) {
    final preset = context.read<PresetManager>();
    final values = config.effectValues;
    
    preset.setTempo(values['tempo'] ?? 1.0);
    preset.setPitch(values['pitch'] ?? 0.0);
    preset.setReverb(values['reverb'] ?? 0.0);
    preset.setDelay(values['delay'] ?? 0.0);
    preset.setBass(values['bass'] ?? 0.0);
    preset.setTrebleCut(values['trebleCut'] ?? 0.0);
    
    preset.setAtmosphere('rain', values['rain'] ?? 0.0);
    preset.setAtmosphere('vinyl', values['vinyl'] ?? 0.0);
    preset.setAtmosphere('wind', values['wind'] ?? 0.0);
    preset.setAtmosphere('tape', values['tape'] ?? 0.0);
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Restored session: ${config.fileName}')),
    );
  }

  void _handleReset() {
    HapticFeedback.mediumImpact();
    final preset = context.read<PresetManager>();
    preset.applyPreset(LofiPreset.normal);
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Reset to default',
          style: GoogleFonts.splineSans(),
        ),
        backgroundColor: const Color(0xFF993DF5),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  void _handleShare() {
    // TODO: Implement share functionality
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          'Share feature coming soon',
          style: GoogleFonts.splineSans(),
        ),
      ),
    );
  }

  void _handleSave({bool silent = false}) async {
    final preset = context.read<PresetManager>();
    final storage = StorageService();
    
    // Create saved config
    final config = SavedConfig(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      fileName: widget.fileName,
      filePath: widget.filePath,
      savedAt: DateTime.now(),
      effectValues: {
        'tempo': preset.tempo,
        'pitch': preset.pitch,
        'reverb': preset.reverb,
        'delay': preset.delay,
        'bass': preset.bass,
        'trebleCut': preset.trebleCut,
        'rain': preset.rainVolume,
        'vinyl': preset.vinylVolume,
        'wind': preset.windVolume,
        'tape': preset.tapeVolume,
      },
    );
    
    await storage.saveConfig(config);
    
    if (mounted && !silent) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Configuration saved!',
            style: GoogleFonts.splineSans(),
          ),
          backgroundColor: const Color(0xFF993DF5),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  void _handleCreatePreset() {
    final TextEditingController nameController = TextEditingController();
    
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: const Color(0xFF2A1F36),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(
          'Create Custom Preset',
          style: GoogleFonts.splineSans(color: Colors.white, fontWeight: FontWeight.bold),
        ),
        content: TextField(
          controller: nameController,
          style: GoogleFonts.splineSans(color: Colors.white),
          decoration: InputDecoration(
            hintText: 'Preset Name',
            hintStyle: GoogleFonts.splineSans(color: Colors.white38),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: Color(0xFF993DF5)),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide(color: Colors.white.withOpacity(0.3)),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: const BorderSide(color: Color(0xFF993DF5), width: 2),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Cancel', style: GoogleFonts.splineSans(color: Colors.white54)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF993DF5),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
            onPressed: () async {
              if (nameController.text.trim().isEmpty) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Please enter a preset name')),
                );
                return;
              }
              
              final preset = context.read<PresetManager>();
              final storage = StorageService();
              
              final customPreset = CustomPreset(
                id: DateTime.now().millisecondsSinceEpoch.toString(),
                name: nameController.text.trim(),
                effectValues: {
                  'tempo': preset.tempo,
                  'pitch': preset.pitch,
                  'reverb': preset.reverb,
                  'delay': preset.delay,
                  'bass': preset.bass,
                  'trebleCut': preset.trebleCut,
                  'rain': preset.rainVolume,
                  'vinyl': preset.vinylVolume,
                  'wind': preset.windVolume,
                  'tape': preset.tapeVolume,
                },
                createdAt: DateTime.now(),
              );
              
              await storage.saveCustomPreset(customPreset);
              
              // Update Manager to show it immediately
              preset.saveCustomPreset(nameController.text.trim());
              
              Navigator.pop(context);
              
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                      'Preset "${nameController.text.trim()}" created!',
                      style: GoogleFonts.splineSans(),
                    ),
                    backgroundColor: const Color(0xFF993DF5),
                    duration: const Duration(seconds: 2),
                  ),
                );
              }
            },
            child: Text('Create', style: GoogleFonts.splineSans(color: Colors.white)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF191022),
      body: Stack(
        children: [
          SafeArea(
            bottom: false,
            child: Column(
              children: [
                _buildHeader(),
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.only(left: 24, right: 24, top: 16, bottom: 220),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildMoodSection(),
                        const SizedBox(height: 32),
                        _buildFineTuneSection(),
                        const SizedBox(height: 32),
                        _buildAtmosphereSection(),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),

          Positioned(
            left: 0,
            right: 0,
            bottom: 0,
            child: _buildBottomPlayerBar(),
          ),

          if (_isExporting) _buildExportOverlay(),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xFF191022).withOpacity(0.9),
      ),
      child: ClipRRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 8, sigmaY: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    margin: const EdgeInsets.only(right: 12),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(20),
                      color: Colors.transparent,
                    ),
                    child: IconButton(
                      padding: EdgeInsets.zero,
                      icon: const Icon(Icons.arrow_back, color: Colors.white70, size: 24),
                      onPressed: () => Navigator.pop(context),
                    ),
                  ),
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
              Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(20),
                      color: Colors.transparent,
                    ),
                    child: IconButton(
                      padding: EdgeInsets.zero,
                      icon: const Icon(Icons.refresh, color: Colors.white70, size: 24),
                      onPressed: _handleReset,
                      tooltip: 'Reset',
                    ),
                  ),
                  const SizedBox(width: 8),
                  PopupMenuButton<String>(
                    icon: const Icon(Icons.more_vert, color: Colors.white70, size: 24),
                    color: const Color(0xFF2A1F36),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                      side: BorderSide(color: Colors.white.withOpacity(0.1)),
                    ),
                    onSelected: _handleMenuSelection,
                    itemBuilder: (context) => [
                      PopupMenuItem(
                        value: 'export',
                        child: Row(
                          children: [
                            const Icon(Icons.file_download, color: Colors.white70, size: 20),
                            const SizedBox(width: 12),
                            Text(
                              'Export',
                              style: GoogleFonts.splineSans(color: Colors.white),
                            ),
                          ],
                        ),
                      ),
                      PopupMenuItem(
                        value: 'share',
                        child: Row(
                          children: [
                            const Icon(Icons.share, color: Colors.white70, size: 20),
                            const SizedBox(width: 12),
                            Text(
                              'Share',
                              style: GoogleFonts.splineSans(color: Colors.white),
                            ),
                          ],
                        ),
                      ),
                      PopupMenuItem(
                        value: 'save',
                        child: Row(
                          children: [
                            const Icon(Icons.save, color: Colors.white70, size: 20),
                            const SizedBox(width: 12),
                            Text(
                              'Save',
                              style: GoogleFonts.splineSans(color: Colors.white),
                            ),
                          ],
                        ),
                      ),
                      PopupMenuItem(
                        value: 'create_preset',
                        child: Row(
                          children: [
                            const Icon(Icons.add_circle, color: Colors.white70, size: 20),
                            const SizedBox(width: 12),
                            Text(
                              'Create Preset',
                              style: GoogleFonts.splineSans(color: Colors.white),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildMoodSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Select Preset',
              style: GoogleFonts.splineSans(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: Colors.white,
                letterSpacing: 0.5,
              ),
            ),
          ],
        ),
        const SizedBox(height: 20),
        SizedBox(
          height: 220,
          child: Consumer<PresetManager>(
            builder: (context, manager, _) {
              return ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.only(left: 24, right: 100), // Show 3rd card partially
                children: [
                  _buildMoodCard(
                    label: 'Normal',
                    subtitle: 'Raw Audio',
                    icon: Icons.music_note,
                    isSelected: manager.currentPreset == LofiPreset.normal,
                    onTap: () {
                      HapticFeedback.mediumImpact();
                      manager.applyPreset(LofiPreset.normal);
                    },
                    gradient: null,
                    iconColor: Colors.white38,
                    isDashed: true,
                  ),
                  _buildMoodCard(
                    label: 'Slow Reverb', // Renamed per request
                    subtitle: 'Deep & Spacious',
                    icon: Icons.bedtime,
                    isSelected: manager.currentPreset == LofiPreset.lofiSlow,
                    onTap: () {
                      HapticFeedback.mediumImpact();
                      manager.applyPreset(LofiPreset.lofiSlow);
                    },
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        const Color(0xFF993DF5).withOpacity(0.25),
                        const Color(0xFF191022).withOpacity(0.05),
                      ],
                    ),
                    iconColor: const Color(0xFF993DF5),
                    progressValue: 0.75,
                  ),
                  _buildMoodCard(
                    label: 'Rainy Night',
                    subtitle: null,
                    icon: Icons.cloudy_snowing,
                    isSelected: manager.currentPreset == LofiPreset.rainyNight,
                    onTap: () {
                      HapticFeedback.mediumImpact();
                      manager.applyPreset(LofiPreset.rainyNight);
                    },
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        const Color(0xFF3B82F6).withOpacity(0.2),
                        const Color(0xFF191022).withOpacity(0),
                      ],
                    ),
                    iconColor: const Color(0xFF60A5FA),
                    progressValue: 0.80,
                  ),
                  _buildMoodCard(
                    label: 'Vintage',
                    subtitle: null,
                    icon: Icons.radio,
                    isSelected: manager.currentPreset == LofiPreset.vintage,
                    onTap: () {
                      HapticFeedback.mediumImpact();
                      manager.applyPreset(LofiPreset.vintage);
                    },
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        const Color(0xFFFBBF24).withOpacity(0.2),
                        const Color(0xFF191022).withOpacity(0),
                      ],
                    ),
                    iconColor: const Color(0xFFFBBF24),
                    progressValue: 0.65,
                  ),
                  _buildMoodCard(
                    label: 'Dreamy',
                    subtitle: null,
                    icon: Icons.cloud,
                    isSelected: manager.currentPreset == LofiPreset.dreamy,
                    onTap: () {
                      HapticFeedback.mediumImpact();
                      manager.applyPreset(LofiPreset.dreamy);
                    },
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        const Color(0xFF8B5CF6).withOpacity(0.2),
                        const Color(0xFF191022).withOpacity(0),
                      ],
                    ),
                    iconColor: const Color(0xFFA78BFA),
                    progressValue: 0.70,
                  ),
                  _buildMoodCard(
                    label: 'Sad',
                    subtitle: null,
                    icon: Icons.sentiment_dissatisfied,
                    isSelected: manager.currentPreset == LofiPreset.sad,
                    onTap: () {
                      HapticFeedback.mediumImpact();
                      manager.applyPreset(LofiPreset.sad);
                    },
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        const Color(0xFF6366F1).withOpacity(0.2),
                        const Color(0xFF191022).withOpacity(0),
                      ],
                    ),
                    iconColor: const Color(0xFF818CF8),
                    progressValue: 0.60,
                  ),
                  
                  // Saved Presets
                  ...manager.savedPresets.asMap().entries.map((entry) {
                    final index = entry.key;
                    final preset = entry.value;
                    final isSelected = manager.currentPreset == LofiPreset.custom && manager.customPresetName == preset['name'];
                    
                    return _buildMoodCard(
                      label: preset['name'],
                      subtitle: 'User Preset',
                      icon: Icons.person_outline,
                      isSelected: isSelected,
                      onTap: () {
                         HapticFeedback.mediumImpact();
                         manager.applySavedPreset(index);
                      },
                      gradient: LinearGradient(
                        colors: [Colors.teal.shade900, Colors.black],
                      ),
                      iconColor: Colors.tealAccent,
                    );
                  }).toList(),
                  
                  if (manager.currentPreset == LofiPreset.custom && manager.customPresetName == null)
                    _buildMoodCard(
                        label: 'Custom',
                        subtitle: 'Modified',
                        icon: Icons.tune,
                        isSelected: true,
                        onTap: () {}, // Already selected
                        gradient: LinearGradient(
                          colors: [Colors.orange.shade900, Colors.black],
                        ),
                        iconColor: Colors.orange,
                        progressValue: 1.0,
                    ),
                ],
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildMoodCard({
    required String label,
    String? subtitle,
    required IconData icon,
    required bool isSelected,
    required VoidCallback onTap,
    Gradient? gradient,
    required Color iconColor,
    bool isDashed = false,
    double? progressValue,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        width: 160,
        margin: const EdgeInsets.only(right: 16),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(24),
          color: isDashed ? Colors.transparent : const Color(0xFF2A1F36),
          border: Border.all(
            color: isSelected
                ? const Color(0xFF993DF5)
                : isDashed
                    ? Colors.white.withOpacity(0.2)
                    : Colors.white.withOpacity(0.05),
            width: isDashed ? 2 : 1,
            style: isDashed ? BorderStyle.solid : BorderStyle.solid,
          ),
          boxShadow: isSelected
              ? [
                  BoxShadow(
                    color: const Color(0xFF993DF5).withOpacity(0.4),
                    blurRadius: 15,
                    spreadRadius: 0,
                  ),
                  BoxShadow(
                    color: const Color(0xFF993DF5).withOpacity(0.2),
                    blurRadius: 10,
                    spreadRadius: 0,
                    offset: const Offset(0, 0),
                  ),
                ]
              : [],
        ),
        child: Stack(
          children: [
            if (gradient != null)
              Container(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(24),
                  gradient: gradient,
                ),
              ),
            if (isSelected && !isDashed)
              Positioned(
                top: -60,
                right: -60,
                child: Container(
                  width: 120,
                  height: 120,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: const Color(0xFF993DF5).withOpacity(0.3),
                  ),
                  child: BackdropFilter(
                    filter: ImageFilter.blur(sigmaX: 40, sigmaY: 40),
                    child: Container(color: Colors.transparent),
                  ),
                ),
              ),
            Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      if (isSelected && !isDashed)
                        Container(
                          width: 24,
                          height: 24,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: const Color(0xFF993DF5),
                            boxShadow: [
                              BoxShadow(
                                color: const Color(0xFF993DF5).withOpacity(0.6),
                                blurRadius: 10,
                              ),
                            ],
                          ),
                          child: const Icon(Icons.check, color: Colors.white, size: 14),
                        ),
                    ],
                  ),
                  Icon(
                    icon,
                    size: 38,
                    color: iconColor,
                    shadows: isSelected && !isDashed
                        ? [
                            Shadow(
                              color: iconColor.withOpacity(0.8),
                              blurRadius: 20,
                            ),
                          ]
                        : [],
                  ),
                  Column(
                    children: [
                      Text(
                        label,
                        style: GoogleFonts.splineSans(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: Colors.white,
                          shadows: isSelected && !isDashed
                              ? [
                                  Shadow(
                                    color: const Color(0xFF993DF5).withOpacity(0.6),
                                    blurRadius: 10,
                                  ),
                                ]
                              : [],
                        ),
                      ),
                      if (subtitle != null)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(
                            subtitle,
                            style: GoogleFonts.splineSans(
                              fontSize: 12,
                              color: Colors.white38,
                            ),
                          ),
                        ),
                      if (progressValue != null)
                        Padding(
                          padding: const EdgeInsets.only(top: 12),
                          child: Container(
                            height: 4,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(2),
                              color: Colors.white.withOpacity(0.1),
                            ),
                            child: FractionallySizedBox(
                              alignment: Alignment.centerLeft,
                              widthFactor: progressValue,
                              child: Container(
                                decoration: BoxDecoration(
                                  borderRadius: BorderRadius.circular(2),
                                  color: isSelected ? const Color(0xFF993DF5) : iconColor,
                                  boxShadow: isSelected
                                      ? [
                                          BoxShadow(
                                            color: const Color(0xFF993DF5).withOpacity(0.5),
                                            blurRadius: 10,
                                          ),
                                        ]
                                      : [],
                                ),
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFineTuneSection() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        color: const Color(0xFF352B42).withOpacity(0.3),
        border: Border.all(
          color: Colors.white.withOpacity(0.05),
          width: 1,
        ),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 4,
                    height: 32,
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(2),
                      color: const Color(0xFF993DF5),
                      boxShadow: [
                        BoxShadow(
                          color: const Color(0xFF993DF5).withOpacity(0.5),
                          blurRadius: 10,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 12),
                  Text(
                    'FINE TUNE',
                    style: GoogleFonts.splineSans(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: Colors.white70,
                      letterSpacing: 2,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              Consumer<PresetManager>(
                builder: (context, preset, _) {
                  return Column(
                    children: [
                      _buildSliderControl(
                        icon: Icons.speed,
                        label: 'Tempo',
                        value: (preset.tempo * 100).toInt(),
                        onChanged: (v) {
                          HapticFeedback.selectionClick();
                          preset.setTempo(v / 100);
                        },
                        min: 75,
                        max: 105,
                      ),
                      const SizedBox(height: 24),
                      _buildSliderControl(
                        icon: Icons.graphic_eq,
                        label: 'Pitch',
                        value: ((preset.pitch + 4) * 10).toInt(),
                        onChanged: (v) {
                          HapticFeedback.selectionClick();
                          preset.setPitch((v / 10) - 4);
                        },
                        min: 0,
                        max: 60,
                        displaySuffix: ' st',
                      ),
                      const SizedBox(height: 24),
                      _buildSliderControl(
                        icon: Icons.blur_on,
                        label: 'Reverb',
                        value: (preset.reverb * 100).toInt(),
                        onChanged: (v) {
                          HapticFeedback.selectionClick();
                          preset.setReverb(v / 100);
                        },
                      ),
                      const SizedBox(height: 24),
                      _buildSliderControl(
                        icon: Icons.repeat,
                        label: 'Delay',
                        value: (preset.delay * 100).toInt(),
                        onChanged: (v) {
                          HapticFeedback.selectionClick();
                          preset.setDelay(v / 100);
                        },
                      ),
                      const SizedBox(height: 24),
                      _buildSliderControl(
                        icon: Icons.waves,
                        label: 'Bass Boost',
                        value: (preset.bass * 100).toInt(),
                        onChanged: (v) {
                          HapticFeedback.selectionClick();
                          preset.setBass(v / 100);
                        },
                      ),
                      const SizedBox(height: 24),
                      _buildSliderControl(
                        icon: Icons.remove_circle_outline,
                        label: 'Treble Cut',
                        value: (preset.trebleCut * 100).toInt(),
                        onChanged: (v) {
                          HapticFeedback.selectionClick();
                          preset.setTrebleCut(v / 100);
                        },
                      ),
                    ],
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSliderControl({
    required IconData icon,
    required String label,
    required int value,
    required ValueChanged<double> onChanged,
    int min = 0,
    int max = 100,
    String displaySuffix = '%',
  }) {
    return Column(
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Icon(icon, color: Colors.white38, size: 14),
                const SizedBox(width: 8),
                Text(
                  label.toUpperCase(),
                  style: GoogleFonts.splineSans(
                    fontSize: 12,
                    fontWeight: FontWeight.w500,
                    color: Colors.white70,
                    letterSpacing: 1.5,
                  ),
                ),
              ],
            ),
            Text(
              '$value$displaySuffix',
              style: GoogleFonts.splineSans(
                fontSize: 12,
                fontWeight: FontWeight.bold,
                color: const Color(0xFF993DF5),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        SliderTheme(
          data: SliderTheme.of(context).copyWith(
            activeTrackColor: const Color(0xFF993DF5),
            inactiveTrackColor: const Color(0xFF2A1F36),
            thumbColor: Colors.white,
            trackHeight: 4,
            overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
          ),
          child: Slider(
            min: min.toDouble(),
            max: max.toDouble(),
            value: value.toDouble().clamp(min.toDouble(), max.toDouble()),
            onChanged: onChanged,
          ),
        ),
      ],
    );
  }

  Widget _buildAtmosphereSection() {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(24),
        color: const Color(0xFF352B42).withOpacity(0.3),
        border: Border.all(
          color: Colors.white.withOpacity(0.05),
          width: 1,
        ),
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: Consumer<PresetManager>(
            builder: (context, preset, _) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Container(
                        width: 4,
                        height: 32,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(2),
                          color: const Color(0xFF3DF5E6),
                          boxShadow: [
                            BoxShadow(
                              color: const Color(0xFF3DF5E6).withOpacity(0.5),
                              blurRadius: 10,
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      Text(
                        'ATMOSPHERE LAYERS',
                        style: GoogleFonts.splineSans(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: Colors.white70,
                          letterSpacing: 2,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),
                  _buildAtmosphereRow('Rain', preset.rainVolume, (v) => preset.setAtmosphere('rain', v)),
                  const SizedBox(height: 16),
                  _buildAtmosphereRow('Vinyl', preset.vinylVolume, (v) => preset.setAtmosphere('vinyl', v)),
                  const SizedBox(height: 16),
                  _buildAtmosphereRow('Wind', preset.windVolume, (v) => preset.setAtmosphere('wind', v)),
                  const SizedBox(height: 16),
                  _buildAtmosphereRow('Tape', preset.tapeVolume, (v) => preset.setAtmosphere('tape', v)),
                ],
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildAtmosphereRow(String label, double volume, Function(double) onChanged) {
    bool isActive = volume > 0;
    return Row(
      children: [
        Icon(
          isActive ? Icons.volume_up : Icons.volume_off,
          size: 16,
          color: isActive ? const Color(0xFF3DF5E6) : Colors.white24,
        ),
        const SizedBox(width: 12),
        SizedBox(
          width: 60,
          child: Text(
            label,
            style: GoogleFonts.splineSans(
              color: isActive ? Colors.white : Colors.white38,
              fontSize: 12,
              fontWeight: FontWeight.w500,
            ),
          ),
        ),
        Expanded(
          child: SliderTheme(
            data: SliderTheme.of(context).copyWith(
              activeTrackColor: const Color(0xFF3DF5E6),
              inactiveTrackColor: Colors.white10,
              thumbColor: isActive ? const Color(0xFF3DF5E6) : Colors.white38,
              trackHeight: 3,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 5),
            ),
            child: Slider(
              value: volume,
              onChanged: (v) {
                HapticFeedback.selectionClick();
                onChanged(v);
              },
            ),
          ),
        ),
        SizedBox(
          width: 35,
          child: Text(
            '${(volume * 100).toInt()}%',
            textAlign: TextAlign.right,
            style: GoogleFonts.splineSans(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              color: isActive ? const Color(0xFF3DF5E6) : Colors.white38,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildBottomPlayerBar() {
    return Container(
      decoration: BoxDecoration(
        color: const Color(0xFF191022).withOpacity(0.95),
        border: const Border(
          top: BorderSide(color: Colors.white10, width: 1),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.4),
            blurRadius: 40,
            offset: const Offset(0, -10),
          ),
        ],
      ),
      child: ClipRRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
          child: SafeArea(
            top: false,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                // Progress bar - YouTube-style Slider with time labels
                StreamBuilder<Duration>(
                  stream: _engine.positionStream,
                  initialData: Duration.zero,
                  builder: (context, posSnapshot) {
                    final position = posSnapshot.data ?? Duration.zero;
                    final duration = _engine.duration;
                    final progressValue = duration.inMilliseconds > 0
                        ? position.inMilliseconds.toDouble()
                        : 0.0;
                    
                    // Format time as mm:ss
                    String formatDuration(Duration d) {
                      String twoDigits(int n) => n.toString().padLeft(2, '0');
                      final minutes = twoDigits(d.inMinutes.remainder(60));
                      final seconds = twoDigits(d.inSeconds.remainder(60));
                      return '$minutes:$seconds';
                    }
                    
                    return Column(
                      children: [
                        SliderTheme(
                          data: SliderThemeData(
                            trackHeight: 3,
                            thumbShape: const RoundSliderThumbShape(
                              enabledThumbRadius: 6,
                              elevation: 2,
                            ),
                            overlayShape: const RoundSliderOverlayShape(overlayRadius: 14),
                            activeTrackColor: const Color(0xFF993DF5),
                            inactiveTrackColor: Colors.white.withOpacity(0.15),
                            thumbColor: const Color(0xFF993DF5),
                            overlayColor: const Color(0xFF993DF5).withOpacity(0.3),
                          ),
                          child: Slider(
                            value: progressValue.clamp(0.0, duration.inMilliseconds.toDouble()),
                            min: 0.0,
                            max: duration.inMilliseconds > 0 ? duration.inMilliseconds.toDouble() : 1.0,
                            onChanged: (value) {
                              final seekPosition = Duration(milliseconds: value.toInt());
                              _engine.seek(seekPosition);
                            },
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 24),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                formatDuration(position),
                                style: GoogleFonts.splineSans(
                                  fontSize: 11,
                                  color: Colors.white.withOpacity(0.6),
                                ),
                              ),
                              Text(
                                formatDuration(duration),
                                style: GoogleFonts.splineSans(
                                  fontSize: 11,
                                  color: Colors.white.withOpacity(0.6),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    );
                  },
                ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  child: Row(
                    children: [
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(8),
                          color: const Color(0xFF2A1F36),
                          border: Border.all(
                            color: Colors.white.withOpacity(0.1),
                            width: 1,
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withOpacity(0.3),
                              blurRadius: 8,
                            ),
                          ],
                        ),
                        child: Stack(
                          children: [
                            Container(
                              decoration: BoxDecoration(
                                borderRadius: BorderRadius.circular(8),
                                gradient: LinearGradient(
                                  begin: Alignment.topLeft,
                                  end: Alignment.bottomRight,
                                  colors: [
                                    const Color(0xFF993DF5).withOpacity(0.2),
                                    Colors.transparent,
                                  ],
                                ),
                              ),
                            ),
                            const Center(
                              child: Icon(Icons.music_note, color: Color(0xFF993DF5), size: 24),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              widget.fileName,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: GoogleFonts.splineSans(
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                              ),
                            ),
                            const SizedBox(height: 2),
                            StreamBuilder<bool>(
                              stream: _engine.isPlayingStream,
                              initialData: false,
                              builder: (context, snapshot) {
                                final isPlaying = snapshot.data ?? false;
                                return Text(
                                  isPlaying ? 'Playing • Lofi Mix' : 'Paused',
                                  style: GoogleFonts.splineSans(
                                    fontSize: 12,
                                    color: Colors.white.withOpacity(0.5),
                                  ),
                                );
                              },
                            ),
                          ],
                        ),
                      ),
                      StreamBuilder<bool>(
                        stream: _engine.isPlayingStream,
                        initialData: false,
                        builder: (context, snapshot) {
                          final isPlaying = snapshot.data ?? false;
                          return Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                icon: const Icon(Icons.skip_previous, color: Colors.white70),
                                iconSize: 28,
                                onPressed: () {
                                  // Skip backward 10 seconds
                                  final currentPos = _engine.position;
                                  final newPos = currentPos - const Duration(seconds: 10);
                                  _engine.seek(newPos > Duration.zero ? newPos : Duration.zero);
                                },
                              ),
                              Container(
                                width: 48,
                                height: 48,
                                margin: const EdgeInsets.symmetric(horizontal: 8),
                                decoration: const BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: Colors.white,
                                  boxShadow: [
                                    BoxShadow(
                                      color: Colors.black26,
                                      blurRadius: 8,
                                    ),
                                  ],
                                ),
                                child: IconButton(
                                  padding: EdgeInsets.zero,
                                  icon: Icon(
                                    isPlaying ? Icons.pause : Icons.play_arrow,
                                    color: const Color(0xFF191022),
                                    size: 28,
                                  ),
                                  onPressed: _engine.togglePlayPause,
                                ),
                              ),
                              IconButton(
                                icon: const Icon(Icons.skip_next, color: Colors.white70),
                                iconSize: 28,
                                onPressed: () {
                                  // Skip forward 10 seconds
                                  final currentPos = _engine.position;
                                  final duration = _engine.duration;
                                  final newPos = currentPos + const Duration(seconds: 10);
                                  _engine.seek(newPos < duration ? newPos : duration);
                                },
                              ),
                              const SizedBox(width: 8),
                              IconButton(
                                icon: Icon(
                                  _engine.isLooping ? Icons.repeat_one : Icons.repeat,
                                  color: _engine.isLooping ? const Color(0xFF993DF5) : Colors.white38,
                                ),
                                iconSize: 24,
                                onPressed: () {
                                  setState(() {
                                    _engine.toggleLoop();
                                  });
                                },
                              ),
                            ],
                          );
                        },
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildExportOverlay() {
    return Container(
      color: Colors.black87,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const CircularProgressIndicator(color: Color(0xFF993DF5)),
            const SizedBox(height: 24),
            Text(
              'Rendering Lofi Mix...',
              style: GoogleFonts.splineSans(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'This happens offline on your device.',
              style: GoogleFonts.splineSans(color: Colors.white54, fontSize: 12),
            ),
            const SizedBox(height: 32),
            OutlinedButton(
              onPressed: _cancelExport,
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: Colors.white30),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
              ),
              child: Text('Cancel', style: GoogleFonts.splineSans(color: Colors.white70)),
            ),
          ],
        ),
      ),
    );
  }
}
