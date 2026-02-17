import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:lofiga/logic/audio_engine.dart';

enum LofiPreset {
  normal,
  slowReverb,
  lofi,
  rainyNight,
  vintage,
  dreamy,
  sad,
  underwater,
  nightcore,
  bassBoosted,
  radio,
  custom,
}

class PresetManager extends ChangeNotifier {
  final AudioEngine _audioEngine;
  Timer? _debounceTimer;

  // Current State
  LofiPreset _currentPreset = LofiPreset.normal;

  // Core Values (mapped to 0.0 - 1.0 or specific ranges)
  double _tempo = 1.0;
  double _pitch = 0.0;
  double _reverb = 0.0;
  double _delay = 0.0;
  double _bass = 0.0;
  double _trebleCut = 0.0;

  // Atmospheres
  double _rainVolume = 0.0;
  double _vinylVolume = 0.0;
  double _windVolume = 0.0;
  double _tapeVolume = 0.0;

  PresetManager(this._audioEngine);

  // Getters
  LofiPreset get currentPreset => _currentPreset;
  double get tempo => _tempo;
  double get pitch => _pitch;
  double get reverb => _reverb;
  double get delay => _delay;
  double get bass => _bass;
  double get trebleCut => _trebleCut;

  double get rainVolume => _rainVolume;
  double get vinylVolume => _vinylVolume;
  double get windVolume => _windVolume;
  double get tapeVolume => _tapeVolume;

  // Debounced Update
  void _scheduleUpdate() {
    if (_debounceTimer?.isActive ?? false) _debounceTimer!.cancel();
    _debounceTimer = Timer(const Duration(milliseconds: 50), () {
      _updateEngine();
    });
  }

  // Setters
  void setTempo(double val) {
    _tempo = val;
    _currentPreset = LofiPreset.custom;
    notifyListeners(); // Immediate UI update
    _scheduleUpdate(); // Debounced DSP update
  }

  void setPitch(double val) {
    _pitch = val;
    _currentPreset = LofiPreset.custom;
    notifyListeners();
    _scheduleUpdate();
  }

  void setReverb(double val) {
    _reverb = val;
    _currentPreset = LofiPreset.custom;
    notifyListeners();
    _scheduleUpdate();
  }

  void setDelay(double val) {
    _delay = val;
    _currentPreset = LofiPreset.custom;
    notifyListeners();
    _scheduleUpdate();
  }

  void setBass(double val) {
    _bass = val;
    _currentPreset = LofiPreset.custom;
    notifyListeners();
    _scheduleUpdate();
  }

  void setTrebleCut(double val) {
    _trebleCut = val;
    _currentPreset = LofiPreset.custom;
    notifyListeners();
    _scheduleUpdate();
  }

  void setAtmosphere(String type, double volume) {
    switch (type) {
      case 'rain': _rainVolume = volume; break;
      case 'vinyl': _vinylVolume = volume; break;
      case 'wind': _windVolume = volume; break;
      case 'tape': _tapeVolume = volume; break;
    }
    _currentPreset = LofiPreset.custom;
    // Direct volume updates are cheap, no debounce needed usually, but consistent behavior is good.
    // However, atmosphere volume is simple gain, usually instant.
    _audioEngine.setAtmosphereVolume(type, volume);
    notifyListeners();
  }

  void applyPreset(LofiPreset preset) {
    _currentPreset = preset;

    // Reset all first
    _tempo = 1.0; _pitch = 0.0; _reverb = 0.0; _delay = 0.0; _bass = 0.0; _trebleCut = 0.0;
    _rainVolume = 0; _vinylVolume = 0; _windVolume = 0; _tapeVolume = 0;

    switch (preset) {
      case LofiPreset.normal:
        // Already reset
        break;
      case LofiPreset.slowReverb:
        _tempo = 0.85; 
        _pitch = -1.0; 
        _reverb = 0.40;
        _bass = 0.20;
        // No Atmosphere
        break;
      case LofiPreset.lofi:
        _tempo = 0.90;
        _pitch = -0.5;
        _trebleCut = 0.40;
        _vinylVolume = 0.15;
        _rainVolume = 0.05;
        break;
      case LofiPreset.rainyNight:
        _tempo = 0.90; 
        _pitch = -1.0; 
        _reverb = 0.50; 
        _delay = 0.15; 
        _trebleCut = 0.50;
        _rainVolume = 0.20; 
        _windVolume = 0.10;
        break;
      case LofiPreset.vintage:
        _tempo = 0.95; 
        _trebleCut = 0.60;
        _vinylVolume = 0.25; 
        _tapeVolume = 0.10;
        break;
      case LofiPreset.dreamy:
        _pitch = -1.5; 
        _reverb = 0.60; 
        _delay = 0.30; 
        _windVolume = 0.10;
        break;
      case LofiPreset.sad:
        _tempo = 0.80; 
        _pitch = -3.0; 
        _reverb = 0.50; 
        _rainVolume = 0.15;
        break;
      case LofiPreset.underwater:
        _trebleCut = 0.80; // Heavy muffled
        _reverb = 0.30;
        _rainVolume = 0.05; // Muffled water sound (simulated)
        break;
      case LofiPreset.nightcore:
        _tempo = 1.25;
        _pitch = 2.0;
        _reverb = 0.10;
        break;
      case LofiPreset.bassBoosted:
        _bass = 0.80;
        _tempo = 1.0;
        break;
      case LofiPreset.radio:
        _trebleCut = 0.30;
        _tapeVolume = 0.20;
        _vinylVolume = 0.10;
        // Simulates limited bandwidth
        break;
      case LofiPreset.custom:
        break; // Keep current
    }

    // Immediate update for presets (no debounce)
    _updateEngine();
    _updateAtmospheres();
    notifyListeners();
  }

  void _updateEngine() {
    _audioEngine.setSpeedAndPitch(_tempo, _pitch);
    _audioEngine.setReverb(_reverb);
    _audioEngine.setDelay(_delay);
    _audioEngine.setLowPass(_trebleCut);
    _audioEngine.setBassBoost(_bass);
  }

  void _updateAtmospheres() {
    _audioEngine.setAtmosphereVolume('rain', _rainVolume);
    _audioEngine.setAtmosphereVolume('vinyl', _vinylVolume);
    _audioEngine.setAtmosphereVolume('wind', _windVolume);
    _audioEngine.setAtmosphereVolume('tape', _tapeVolume);
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    super.dispose();
  }
}
