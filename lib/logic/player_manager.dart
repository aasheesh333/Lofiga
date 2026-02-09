import 'package:flutter/material.dart';
import 'package:just_audio/just_audio.dart';

enum AudioEffectPreset { none, lofi, nightcore }

class PlayerManager extends ChangeNotifier {
  // Audio Player Instance
  final AudioPlayer _audioPlayer = AudioPlayer();
  
  // Current Track Info
  String? _currentFilePath;
  String? _currentFileName;
  
  // Playback State
  bool _isPlaying = false;
  Duration _duration = Duration.zero;
  Duration _position = Duration.zero;
  
  // Effects State (Live adjustable)
  double _speed = 1.0;
  double _pitch = 1.0;
  AudioEffectPreset _currentPreset = AudioEffectPreset.none;
  
  // Getters
  AudioPlayer get audioPlayer => _audioPlayer;
  String? get currentFilePath => _currentFilePath;
  String? get currentFileName => _currentFileName;
  bool get isPlaying => _isPlaying;
  Duration get duration => _duration;
  Duration get position => _position;
  double get speed => _speed;
  double get pitch => _pitch;
  AudioEffectPreset get currentPreset => _currentPreset;
  bool get hasTrack => _currentFilePath != null;

  PlayerManager() {
    _initListeners();
  }

  void _initListeners() {
    // Listen to playback state changes
    _audioPlayer.playerStateStream.listen((state) {
      _isPlaying = state.playing;
      notifyListeners();
    });

    // Listen to position changes
    _audioPlayer.positionStream.listen((position) {
      _position = position;
      notifyListeners();
    });

    // Listen to duration changes
    _audioPlayer.durationStream.listen((duration) {
      if (duration != null) {
        _duration = duration;
        notifyListeners();
      }
    });
  }

  /// Load a new track and start playing
  Future<void> loadTrack(String filePath, String fileName) async {
    try {
      _currentFilePath = filePath;
      _currentFileName = fileName;
      
      await _audioPlayer.setFilePath(filePath);
      
      // Reset effects to default (None preset)
      await _applyPreset(AudioEffectPreset.none);
      
      // Auto-play
      await _audioPlayer.play();
      
      notifyListeners();
    } catch (e) {
      debugPrint('Error loading track: $e');
    }
  }

  /// Play/Pause toggle
  Future<void> togglePlayPause() async {
    if (_isPlaying) {
      await _audioPlayer.pause();
    } else {
      await _audioPlayer.play();
    }
  }

  /// Seek to position
  Future<void> seek(Duration position) async {
    await _audioPlayer.seek(position);
  }

  /// Set speed (LIVE - applies immediately)
  Future<void> setSpeed(double speed) async {
    _speed = speed;
    await _audioPlayer.setSpeed(speed);
    _currentPreset = AudioEffectPreset.none; // Custom = None preset
    notifyListeners();
  }

  /// Set pitch (LIVE - applies immediately)
  Future<void> setPitch(double pitch) async {
    _pitch = pitch;
    await _audioPlayer.setPitch(pitch);
    _currentPreset = AudioEffectPreset.none; // Custom = None preset
    notifyListeners();
  }

  /// Apply preset (LIVE)
  Future<void> applyPreset(AudioEffectPreset preset) async {
    await _applyPreset(preset);
    notifyListeners();
  }

  Future<void> _applyPreset(AudioEffectPreset preset) async {
    _currentPreset = preset;
    
    switch (preset) {
      case AudioEffectPreset.none:
        _speed = 1.0;
        _pitch = 1.0;
        await _audioPlayer.setSpeed(1.0);
        await _audioPlayer.setPitch(1.0);
        break;
      case AudioEffectPreset.lofi:
        _speed = 0.85;
        _pitch = 0.85;
        await _audioPlayer.setSpeed(0.85);
        await _audioPlayer.setPitch(0.85);
        break;
      case AudioEffectPreset.nightcore:
        _speed = 1.25;
        _pitch = 1.25;
        await _audioPlayer.setSpeed(1.25);
        await _audioPlayer.setPitch(1.25);
        break;
    }
  }

  @override
  void dispose() {
    _audioPlayer.dispose();
    super.dispose();
  }
}
