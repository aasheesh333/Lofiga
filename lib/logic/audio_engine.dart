import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_soloud/flutter_soloud.dart';
import 'package:just_audio/just_audio.dart' as ja; // Alias just_audio
import 'package:audio_session/audio_session.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import 'dart:math' as math;

/// Singleton class to handle low-level audio processing using SoLoud.
/// This class manages the sound handles, filters, and playback state.
class AudioEngine {
  static final AudioEngine _instance = AudioEngine._internal();
  factory AudioEngine() => _instance;
  AudioEngine._internal();

  final _log = Logger('AudioEngine');

  // SoLoud Instance
  SoLoud? _soloud;

  // Handles
  SoundHandle? _musicHandle;
  // REMOVED: final Map<String, SoundHandle> _atmosphereHandles = {};

  // Sources
  AudioSource? _musicSource;
  // REMOVED: final Map<String, AudioSource> _atmosphereSources = {};

  // Atmosphere Players (JustAudio)
  final Map<String, ja.AudioPlayer> _atmospherePlayers = {};
  final Map<String, double> _atmosphereVolumes = {};

  // State
  bool _isInitialized = false;
  bool _isPlaying = false;
  bool _isLooping = false;
  Duration _duration = Duration.zero;
  Duration _position = Duration.zero;

  // Stream Controllers
  final _positionController = StreamController<Duration>.broadcast();
  final _stateController = StreamController<bool>.broadcast();

  Stream<Duration> get positionStream => _positionController.stream;
  Stream<bool> get isPlayingStream => _stateController.stream;
  Duration get duration => _duration;
  Duration get position => _position;
  bool get isPlaying => _isPlaying;
  bool get isLooping => _isLooping;
  bool get isInitialized => _isInitialized;

  // Initialize Engine
  Future<void> init() async {
    if (_isInitialized) return;

    try {
      _soloud = SoLoud.instance;
      await _soloud!.init();
      _soloud!.setGlobalVolume(1.0);
      
      // Configure Audio Session for iOS (Playback + Mixing)
      final session = await AudioSession.instance;
      await session.configure(const AudioSessionConfiguration.music());

      _isInitialized = true;
      _log.info('SoLoud initialized');

      // Preload Atmospheres
      await _preloadAtmospheres();

      // Start position polling loop
      _startPositionPolling();
    } catch (e) {
      _log.severe('Failed to initialize SoLoud: $e');
    }
  }

  Future<void> _preloadAtmospheres() async {
    // Initialize JustAudio players for atmospheres
    await _initAtmospherePlayer('rain', 'assets/audio/atmosphere/rain_loop.wav');
    await _initAtmospherePlayer('vinyl', 'assets/audio/atmosphere/vinyl_crackle.wav');
    await _initAtmospherePlayer('wind', 'assets/audio/atmosphere/wind_blow.wav');
    await _initAtmospherePlayer('tape', 'assets/audio/atmosphere/tape_hiss.wav');
  }

  Future<void> _initAtmospherePlayer(String key, String assetPath) async {
    try {
      final player = ja.AudioPlayer();
      await player.setAsset(assetPath);
      await player.setLoopMode(ja.LoopMode.all);
      await player.setVolume(0.0); // Start silent
      _atmospherePlayers[key] = player;
      _atmosphereVolumes[key] = 0.0;
      _log.info('Initialized atmosphere player: $key');
    } catch (e) {
      _log.severe('Failed to init atmosphere $key: $e');
    }
  }

  // Load Main Track
  Future<void> loadTrack(String filePath) async {
    if (!_isInitialized) await init();

    try {
      // Stop existing
      stop();

      _musicSource = await _soloud!.loadFile(filePath);
      _duration = _soloud!.getLength(_musicSource!);

      // Play immediately
      _musicHandle = await _soloud!.play(_musicSource!, paused: false);
      _isPlaying = true;
      _stateController.add(true);
      _syncAtmospheres(); // Ensure atmospheres sync with playback

      // Enable Filters Global
      try {
        _soloud!.filters.biquadResonantFilter.activate();
        _soloud!.filters.echoFilter.activate();
        _soloud!.filters.freeverbFilter.activate();
        _soloud!.filters.bassBoostFilter.activate();

        // Add Limiter Filter to prevent clipping
        _soloud!.filters.limiterFilter.activate();
      } catch (e) {
         _log.warning('Filter activation warning: $e');
      }

      // Reset filter params (Global)
      _soloud!.filters.freeverbFilter.wet.value = 0;
      _soloud!.filters.echoFilter.wet.value = 0;
      // Reset Biquad to disabled state (very high freq)
      _soloud!.filters.biquadResonantFilter.frequency.value = 22000;
      // Reset BassBoost
      _soloud!.filters.bassBoostFilter.wet.value = 0;
      // Set Limiter defaults (Threshold -1dB)
      _soloud!.filters.limiterFilter.threshold.value = -1.0;
      _soloud!.filters.limiterFilter.outputCeiling.value = -0.5;

      _log.info('Track loaded: $filePath');
    } catch (e) {
       _log.severe('Error loading track or filters: $e');
    }
  }

  // Play / Pause
  Future<void> togglePlayPause() async {
  if (_musicHandle == null || _soloud == null || _musicSource == null) return;

  if (_isPlaying) {
    // Currently playing, so pause
    _soloud!.setPause(_musicHandle!, true);
    _isPlaying = false;
    _stateController.add(false);
    WakelockPlus.disable(); // Release wakelock when paused
    _syncAtmospheres();
  } else {
    // Currently not playing, so play/resume
    // Check if handle is still valid
    final bool isHandleValid = _musicSource!.handles.contains(_musicHandle!);
    
    if (!isHandleValid) {
      // Handle is invalid (song finished), restart from source
      _musicHandle = await _soloud!.play(_musicSource!, paused: false);
      _position = Duration.zero;
      _positionController.add(_position);
    } else {
      // Handle is valid, just unpause
      // If at start (manually reset), seek to 0 for clean replay
      if (_position.inMilliseconds <= 100) {
        _soloud!.seek(_musicHandle!, Duration.zero);
      }
      _soloud!.setPause(_musicHandle!, false);
    }
    
    _isPlaying = true;
    _stateController.add(true);
    WakelockPlus.enable(); // Keep screen/CPU awake during playback
    _syncAtmospheres(); // Sync atmospheres with play state
  }
}

  void seek(Duration position) {
    if (_musicHandle == null || _soloud == null) return;
    _soloud!.seek(_musicHandle!, position);
    _position = position;
    _positionController.add(position);
  }

  void toggleLoop() {
    _isLooping = !_isLooping;
    if (_musicHandle != null && _soloud != null) {
      _soloud!.setLooping(_musicHandle!, _isLooping);
    }
  }

  // Stop all playback (used when leaving editor)
  void stop() {
    _isPlaying = false; // Set this BEFORE sync so atmospheres know to pause
    WakelockPlus.disable(); // Ensure wakelock is released
    _syncAtmospheres(); 

    if (_musicHandle != null && _soloud != null) {
      _soloud!.stop(_musicHandle!);
      _musicHandle = null;
    }
    if (_musicSource != null && _soloud != null) {
      _soloud!.disposeSource(_musicSource!);
      _musicSource = null;
    }
    
    _stateController.add(false);
    _position = Duration.zero;
    _positionController.add(_position);
  }

  // --- DSP Controls ---

  // Speed & Pitch (Varispeed: changing speed changes pitch)
  // Limitation: Independent Time-Stretch is NOT supported.
  // This implements "Tape Style" speed change.
  void setSpeedAndPitch(double tempo, double semitones) {
    if (_musicHandle == null || _soloud == null) return;

    double pitchFactor = 1.0;
    if (semitones != 0) {
      pitchFactor = _semitoneToFactor(semitones);
    }

    double combinedSpeed = tempo * pitchFactor;

    // Safety clamp
    if (combinedSpeed < 0.1) combinedSpeed = 0.1;
    if (combinedSpeed > 3.0) combinedSpeed = 3.0;

    _soloud!.setRelativePlaySpeed(_musicHandle!, combinedSpeed);
  }

  double _semitoneToFactor(double semitones) {
    // Correct Logarithmic Pitch Calculation: 2^(n/12)
    return math.pow(2, semitones / 12.0).toDouble();
  }

  // Filters (Global)
  // These update existing filter parameters, they do NOT recreate nodes.

  void setReverb(double wet) {
    if (_soloud == null) return;

    if (wet > 0) {
       _soloud!.filters.freeverbFilter.wet.value = wet;
       _soloud!.filters.freeverbFilter.roomSize.value = wet;
       _soloud!.filters.freeverbFilter.damp.value = 0.5;
       _soloud!.filters.freeverbFilter.width.value = 1.0;
       _soloud!.filters.freeverbFilter.freeze.value = 0.0;
    } else {
       _soloud!.filters.freeverbFilter.wet.value = 0.0;
    }
  }

  void setDelay(double wet) {
    if (_soloud == null) return;
    _soloud!.filters.echoFilter.wet.value = wet;
    _soloud!.filters.echoFilter.delay.value = 0.5;
    _soloud!.filters.echoFilter.decay.value = 0.5;
  }

  void setLowPass(double cutoffFactor) {
    if (_soloud == null) return;

    if (cutoffFactor > 0) {
      double freq = 20000 - (cutoffFactor * 18000); // 0% = 20kHz, 100% = 2kHz
      _soloud!.filters.biquadResonantFilter.type.value = 0; // LowPass
      _soloud!.filters.biquadResonantFilter.frequency.value = freq;
      _soloud!.filters.biquadResonantFilter.resonance.value = 2.0;
    } else {
      _soloud!.filters.biquadResonantFilter.frequency.value = 22000;
    }
  }

  void setBassBoost(double strength) {
    if (_soloud == null) return;

    if (strength > 0) {
      _soloud!.filters.bassBoostFilter.wet.value = 1.0;
      _soloud!.filters.bassBoostFilter.boost.value = strength * 5.0; // Scale up
    } else {
      _soloud!.filters.bassBoostFilter.wet.value = 0.0;
    }
  }

  // --- Atmosphere ---

  // --- Atmosphere (JustAudio Implementation) ---

  void setAtmosphereVolume(String key, double volume) {
    _atmosphereVolumes[key] = volume;
    _updateAtmosphereState(key);
  }

  void _updateAtmosphereState(String key) {
    final player = _atmospherePlayers[key];
    if (player == null) return;

    final vol = _atmosphereVolumes[key] ?? 0.0;
    // Play IF volume > 0 AND main music is playing
    // (Or should we allow preview? For now, stick to sync)
    bool shouldPlay = vol > 0.01 && _isPlaying;

    if (shouldPlay) {
      if (!player.playing) player.play();
    } else {
      if (player.playing) player.pause();
    }
    player.setVolume(vol);
  }

  void _syncAtmospheres() {
    for (var key in _atmospherePlayers.keys) {
      _updateAtmosphereState(key);
    }
  }

  // Obsolete: loadAtmosphere, _copyAssetToLocal, _startAtmosphere, _playAtmospheres, _pauseAtmospheres
  // Removed to clean up SoLoud atmosphere logic.

  // Obsolete methods removed.

  // Internal Loop
  void _startPositionPolling() {
    Timer.periodic(const Duration(milliseconds: 100), (timer) {
      if (_musicHandle != null && _soloud != null) {
        // Query actual position from SoLoud
        final actualPosition = _soloud!.getPosition(_musicHandle!);
        _position = actualPosition;
        
        if (_isPlaying) {
        // Check if song has finished (not looping and reached/passed end OR invalid handle)
        final bool isValid = _musicSource != null && _musicSource!.handles.contains(_musicHandle!);
        
        if ((!isValid || (_position >= _duration && _duration.inMilliseconds > 0)) && !_isLooping) {
          // Song has finished - stop playback but keep position at end
          _isPlaying = false;
          _stateController.add(false);
          _syncAtmospheres(); // Pauses atmospheres because _isPlaying is false
          // Don't reset position to zero - this breaks handle validity check in togglePlayPause
        } else {
          // Still playing, emit current position
          if (isValid) {
             _positionController.add(_position);
          }
        }
        }
      }
    });
  }

  void dispose() {
    // Note: Since this is a singleton provided at app level,
    // calling dispose() shuts down the engine globally.
    _soloud?.deinit();
    for (var player in _atmospherePlayers.values) {
      player.dispose();
    }
    _positionController.close();
    _stateController.close();
  }
}
