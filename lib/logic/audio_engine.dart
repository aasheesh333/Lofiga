import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_soloud/flutter_soloud.dart';
import 'package:logging/logging.dart';
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
  final Map<String, SoundHandle> _atmosphereHandles = {};

  // Sources
  AudioSource? _musicSource;
  final Map<String, AudioSource> _atmosphereSources = {};

  // State
  bool _isInitialized = false;
  bool _isPlaying = false;
  Duration _duration = Duration.zero;
  Duration _position = Duration.zero;

  // Stream Controllers
  final _positionController = StreamController<Duration>.broadcast();
  final _stateController = StreamController<bool>.broadcast();

  Stream<Duration> get positionStream => _positionController.stream;
  Stream<bool> get isPlayingStream => _stateController.stream;
  Duration get duration => _duration;
  bool get isPlaying => _isPlaying;
  bool get isInitialized => _isInitialized;

  // Initialize Engine
  Future<void> init() async {
    if (_isInitialized) return;

    try {
      _soloud = SoLoud.instance;
      await _soloud!.init();
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
    await loadAtmosphere('rain', 'assets/audio/atmosphere/rain_loop.mp3');
    await loadAtmosphere('vinyl', 'assets/audio/atmosphere/vinyl_crackle.mp3');
    await loadAtmosphere('wind', 'assets/audio/atmosphere/wind_blow.mp3');
    await loadAtmosphere('tape', 'assets/audio/atmosphere/tape_hiss.mp3');
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
  void togglePlayPause() {
    if (_musicHandle == null || _soloud == null) return;

    _soloud!.getPause(_musicHandle!)
      ? _soloud!.setPause(_musicHandle!, false)
      : _soloud!.setPause(_musicHandle!, true);

    _isPlaying = !_soloud!.getPause(_musicHandle!);
    _stateController.add(_isPlaying);

    // Also toggle atmospheres
    if (_isPlaying) {
      _playAtmospheres();
    } else {
      _pauseAtmospheres();
    }
  }

  void seek(Duration position) {
    if (_musicHandle == null || _soloud == null) return;
    _soloud!.seek(_musicHandle!, position);
    _position = position;
    _positionController.add(position);
  }

  // Stop all playback (used when leaving editor)
  void stop() {
    if (_musicHandle != null && _soloud != null) {
      _soloud!.stop(_musicHandle!);
      _musicHandle = null;
    }
    _pauseAtmospheres();
    _isPlaying = false;
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
      double freq = 20000 - (cutoffFactor * 19000);
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

  Future<void> loadAtmosphere(String key, String assetPath) async {
    if (!_isInitialized) return;
    try {
      if (!_atmosphereSources.containsKey(key)) {
        final source = await _soloud!.loadAsset(assetPath);
        _atmosphereSources[key] = source;
      }
    } catch (e) {
      _log.warning('Failed to load atmosphere $key: $e');
    }
  }

  void setAtmosphereVolume(String key, double volume) {
    final handle = _atmosphereHandles[key];
    if (handle != null) {
      // Handle exists, just update volume
      _soloud!.setVolume(handle, volume);

      // Optimization: if volume is effectively zero, maybe pause?
      // For now, keeping it simple to ensure instant response.
    } else if (volume > 0.01) {
      // Start if not playing and volume is requested
      _startAtmosphere(key, volume);
    }
  }

  Future<void> _startAtmosphere(String key, double volume) async {
    final source = _atmosphereSources[key];
    if (source != null) {
       // Loop
       final handle = await _soloud!.play(source, volume: volume, looping: true, paused: !_isPlaying);
       _atmosphereHandles[key] = handle;
    }
  }

  void _playAtmospheres() {
    for (var handle in _atmosphereHandles.values) {
      _soloud!.setPause(handle, false);
    }
  }

  void _pauseAtmospheres() {
     for (var handle in _atmosphereHandles.values) {
      _soloud!.setPause(handle, true);
    }
  }

  // Internal Loop
  void _startPositionPolling() {
    Timer.periodic(const Duration(milliseconds: 100), (timer) {
      if (_musicHandle != null && _isPlaying) {
        if (_position < _duration) {
          _position += const Duration(milliseconds: 100);
          _positionController.add(_position);
        }
      }
    });
  }

  void dispose() {
    // Note: Since this is a singleton provided at app level,
    // calling dispose() shuts down the engine globally.
    _soloud?.deinit();
    _positionController.close();
    _stateController.close();
  }
}
