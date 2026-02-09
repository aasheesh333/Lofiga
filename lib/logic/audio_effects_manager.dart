import 'package:just_audio/just_audio.dart';

enum AudioEffectPreset {
  normal,
  lofi,
  nightcore,
}

class AudioEffectsManager {
  final AudioPlayer _player;

  AudioEffectsManager(this._player);

  AudioEffectPreset _currentPreset = AudioEffectPreset.normal;
  AudioEffectPreset get currentPreset => _currentPreset;

  Future<void> setPreset(AudioEffectPreset preset) async {
    _currentPreset = preset;
    switch (preset) {
      case AudioEffectPreset.normal:
        await _player.setSpeed(1.0);
        await _player.setPitch(1.0);
        break;
      case AudioEffectPreset.lofi:
        // Slowed + Reverb vibe (simulated via pitch/speed)
        // Note: Real reverb requires platform specific DSP or complex plugins.
        // For Lofi feel, we slow it down significantly.
        await _player.setSpeed(0.85); 
        await _player.setPitch(0.85);
        break;
      case AudioEffectPreset.nightcore:
        // Fast and high pitched
        await _player.setSpeed(1.25);
        await _player.setPitch(1.25);
        break;
    }
  }
}
