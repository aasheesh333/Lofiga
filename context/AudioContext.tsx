import React, { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import { Audio, AVPlaybackStatus } from 'expo-av';
import { Alert } from 'react-native';

interface AudioContextType {
  sound: Audio.Sound | null;
  isPlaying: boolean;
  duration: number;
  position: number;
  rate: number;
  volume: number;
  fileName: string | null;
  isLoading: boolean;
  loadAudio: (uri: string, name: string) => Promise<void>;
  togglePlay: () => Promise<void>;
  updateRate: (rate: number, correctPitch?: boolean) => Promise<void>;
  updateVolume: (volume: number) => Promise<void>;
  seekTo: (position: number) => Promise<void>;
  unloadAudio: () => Promise<void>;
}

const AudioContext = createContext<AudioContextType | undefined>(undefined);

export const useAudio = () => {
  const context = useContext(AudioContext);
  if (!context) throw new Error("useAudio must be used within an AudioProvider");
  return context;
};

export const AudioProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [sound, setSound] = useState<Audio.Sound | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [duration, setDuration] = useState(0);
  const [position, setPosition] = useState(0);
  const [rate, setRate] = useState(1.0);
  const [volume, setVolume] = useState(1.0);
  const [fileName, setFileName] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  // Setup Audio Mode
  useEffect(() => {
    Audio.setAudioModeAsync({
      allowsRecordingIOS: false,
      staysActiveInBackground: true,
      playsInSilentModeIOS: true,
      shouldDuckAndroid: true,
      playThroughEarpieceAndroid: false,
    });
  }, []);

  const onPlaybackStatusUpdate = useCallback((status: AVPlaybackStatus) => {
    if (status.isLoaded) {
      setDuration(status.durationMillis || 0);
      setPosition(status.positionMillis);
      setIsPlaying(status.isPlaying);
      setRate(status.rate);
      setVolume(status.volume);

      if (status.didJustFinish) {
        setIsPlaying(false);
        // Loop is usually handled by isLooping option, or manual seek
      }
    }
  }, []);

  const loadAudio = async (uri: string, name: string) => {
    try {
      setIsLoading(true);
      if (sound) {
        await sound.unloadAsync();
      }

      const { sound: newSound } = await Audio.Sound.createAsync(
        { uri },
        { shouldPlay: true, isLooping: true, rate: rate, volume: volume },
        onPlaybackStatusUpdate
      );

      setSound(newSound);
      setFileName(name);
      setIsPlaying(true);
    } catch (error) {
      console.error("Error loading audio", error);
      Alert.alert("Error", "Could not load audio file.");
    } finally {
      setIsLoading(false);
    }
  };

  const togglePlay = async () => {
    if (!sound) return;
    if (isPlaying) {
      await sound.pauseAsync();
    } else {
      await sound.playAsync();
    }
  };

  const updateRate = async (newRate: number, correctPitch: boolean = true) => {
    if (!sound) return;
    // Debounce or just set? calling async frequently is bad.
    // We update state immediately for UI, but await the sound call?
    // For performance, usually we don't await in the slider callback directly if it blocks.
    try {
        await sound.setRateAsync(newRate, correctPitch);
        setRate(newRate);
    } catch (e) {
        console.error(e);
    }
  };

  const updateVolume = async (newVolume: number) => {
    if (!sound) return;
    try {
        await sound.setVolumeAsync(newVolume);
        setVolume(newVolume);
    } catch (e) {
        console.error(e);
    }
  };

  const seekTo = async (pos: number) => {
    if (!sound) return;
    await sound.setPositionAsync(pos);
  };

  const unloadAudio = async () => {
      if (sound) {
          await sound.unloadAsync();
          setSound(null);
          setFileName(null);
          setPosition(0);
          setDuration(0);
      }
  }

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (sound) {
        sound.unloadAsync();
      }
    };
  }, [sound]);

  return (
    <AudioContext.Provider
      value={{
        sound,
        isPlaying,
        duration,
        position,
        rate,
        volume,
        fileName,
        isLoading,
        loadAudio,
        togglePlay,
        updateRate,
        updateVolume,
        seekTo,
        unloadAudio
      }}
    >
      {children}
    </AudioContext.Provider>
  );
};
