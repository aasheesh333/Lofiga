import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';

/// Model class for saved audio configurations
class SavedConfig {
  final String id;
  final String fileName;
  final String filePath;
  final DateTime savedAt;
  final Map<String, double> effectValues;

  SavedConfig({
    required this.id,
    required this.fileName,
    required this.filePath,
    required this.savedAt,
    required this.effectValues,
  });

  Map<String, dynamic> toJson() => {
    'id': id,
    'fileName': fileName,
    'filePath': filePath,
    'savedAt': savedAt.toIso8601String(),
    'effectValues': effectValues,
  };

  factory SavedConfig.fromJson(Map<String, dynamic> json) => SavedConfig(
    id: json['id'],
    fileName: json['fileName'],
    filePath: json['filePath'],
    savedAt: DateTime.parse(json['savedAt']),
    effectValues: Map<String, double>.from(json['effectValues']),
  );
}

/// Model class for custom presets created by user
class CustomPreset {
  final String id;
  final String name;
  final Map<String, double> effectValues;
  final DateTime createdAt;

  CustomPreset({
    required this.id,
    required this.name,
    required this.effectValues,
    required this.createdAt,
  });

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'effectValues': effectValues,
    'createdAt': createdAt.toIso8601String(),
  };

  factory CustomPreset.fromJson(Map<String, dynamic> json) => CustomPreset(
    id: json['id'],
    name: json['name'],
    effectValues: Map<String, double>.from(json['effectValues']),
    createdAt: DateTime.parse(json['createdAt']),
  );
}

/// Storage service for managing saved configurations and custom presets
class StorageService {
  static const String _savedConfigsKey = 'saved_configs';
  static const String _customPresetsKey = 'custom_presets';

  /// Save a configuration (overwrites if same filePath exists)
  Future<void> saveConfig(SavedConfig config) async {
    final prefs = await SharedPreferences.getInstance();
    final configs = await loadAllConfigs();
    
    // Remove existing config for the same file if it exists
    configs.removeWhere((c) => c.filePath == config.filePath);
    
    configs.add(config);
    
    final jsonList = configs.map((c) => c.toJson()).toList();
    await prefs.setString(_savedConfigsKey, jsonEncode(jsonList));
  }

  /// Load all saved configurations
  Future<List<SavedConfig>> loadAllConfigs() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_savedConfigsKey);
    
    if (jsonString == null) return [];
    
    final jsonList = jsonDecode(jsonString) as List;
    return jsonList.map((json) => SavedConfig.fromJson(json)).toList();
  }

  /// Delete a saved configuration
  Future<void> deleteConfig(String id) async {
    final prefs = await SharedPreferences.getInstance();
    final configs = await loadAllConfigs();
    configs.removeWhere((c) => c.id == id);
    
    final jsonList = configs.map((c) => c.toJson()).toList();
    await prefs.setString(_savedConfigsKey, jsonEncode(jsonList));
  }

  /// Save a custom preset
  Future<void> saveCustomPreset(CustomPreset preset) async {
    final prefs = await SharedPreferences.getInstance();
    final presets = await loadCustomPresets();
    presets.add(preset);
    
    final jsonList = presets.map((p) => p.toJson()).toList();
    await prefs.setString(_customPresetsKey, jsonEncode(jsonList));
  }

  /// Load all custom presets
  Future<List<CustomPreset>> loadCustomPresets() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonString = prefs.getString(_customPresetsKey);
    
    if (jsonString == null) return [];
    
    final jsonList = jsonDecode(jsonString) as List;
    return jsonList.map((json) => CustomPreset.fromJson(json)).toList();
  }

  /// Delete a custom preset
  Future<void> deleteCustomPreset(String id) async {
    final prefs = await SharedPreferences.getInstance();
    final presets = await loadCustomPresets();
    presets.removeWhere((p) => p.id == id);
    
    final jsonList = presets.map((p) => p.toJson()).toList();
    await prefs.setString(_customPresetsKey, jsonEncode(jsonList));
  }
}
