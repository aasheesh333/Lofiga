import { View, Text, TouchableOpacity, ScrollView } from 'react-native';
import { useRouter } from 'expo-router';
import Slider from '@react-native-community/slider';
import { useAudio } from '../context/AudioContext';
import { MaterialIcons, Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { useState } from 'react';
import Animated, { useAnimatedStyle, useSharedValue, withRepeat, withTiming, Easing } from 'react-native-reanimated';

export default function PlayerScreen() {
    const { isPlaying, togglePlay, rate, updateRate, volume, updateVolume, fileName } = useAudio();
    const router = useRouter();
    const [vinylMode, setVinylMode] = useState(true); // Default to Vinyl mode (pitch changes with speed)

    const handleRateChange = (val: number) => {
        // Invert Vinyl Mode logic: if Vinyl Mode is ON, correctPitch is FALSE.
        updateRate(val, !vinylMode);
    };

    const toggleVinylMode = () => {
        const newMode = !vinylMode;
        setVinylMode(newMode);
        updateRate(rate, !newMode);
    };

    return (
        <View className="flex-1 bg-[#191022]">
            <LinearGradient
                colors={['#191022', '#2e1065']}
                style={{ position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 }}
            />

            {/* Header */}
            <View className="flex-row items-center justify-between p-6 pt-12">
                <TouchableOpacity onPress={() => router.back()} className="w-10 h-10 items-center justify-center rounded-full bg-white/10">
                    <MaterialIcons name="arrow-back" size={24} color="white" />
                </TouchableOpacity>
                <Text className="text-white text-lg font-bold">Player</Text>
                <View className="w-10" />
            </View>

            <ScrollView contentContainerStyle={{ paddingBottom: 40 }}>
                {/* Visualizer (Placeholder) */}
                <View className="items-center justify-center my-8">
                    <View className="w-64 h-64 rounded-xl bg-white/5 border border-white/10 items-center justify-center shadow-2xl">
                         <MaterialIcons name="music-note" size={64} color="#993df5" />
                         <View className="absolute bottom-4 flex-row gap-1 h-8 items-end">
                             {[...Array(5)].map((_, i) => (
                                 <View key={i} className="w-1 bg-primary rounded-full" style={{ height: 20 + Math.random() * 20, opacity: 0.6 }} />
                             ))}
                         </View>
                    </View>
                    <Text className="text-white text-xl font-bold mt-6 text-center px-6" numberOfLines={1}>{fileName || "Unknown Track"}</Text>
                </View>

                {/* Controls */}
                <View className="px-6 gap-8">
                    {/* Play/Pause */}
                    <View className="flex-row items-center justify-center gap-8">
                        <TouchableOpacity onPress={togglePlay} className="w-16 h-16 rounded-full bg-primary items-center justify-center shadow-lg shadow-primary/50">
                            <MaterialIcons name={isPlaying ? "pause" : "play-arrow"} size={40} color="white" />
                        </TouchableOpacity>
                    </View>

                    {/* Sliders */}
                    <View className="bg-white/5 p-4 rounded-2xl border border-white/10">
                        <View className="flex-row justify-between mb-2">
                            <Text className="text-white/70 font-medium">Speed (Lofi Factor)</Text>
                            <Text className="text-primary font-bold">{Math.round(rate * 100)}%</Text>
                        </View>
                        <Slider
                            style={{ width: '100%', height: 40 }}
                            minimumValue={0.5}
                            maximumValue={1.2}
                            value={rate}
                            onSlidingComplete={handleRateChange} // Only update on release for perf? Or onValueChange if throttled. AudioContext handles async.
                            // onValueChange causes lag with expo-av async calls if too frequent.
                            minimumTrackTintColor="#993df5"
                            maximumTrackTintColor="#FFFFFF50"
                            thumbTintColor="#FFFFFF"
                        />
                    </View>

                    <View className="bg-white/5 p-4 rounded-2xl border border-white/10">
                        <View className="flex-row justify-between mb-2">
                            <Text className="text-white/70 font-medium">Vinyl Mode (Pitch Shift)</Text>
                            <TouchableOpacity onPress={toggleVinylMode}>
                                <Text className={vinylMode ? "text-primary font-bold" : "text-white/40"}>{vinylMode ? "ON" : "OFF"}</Text>
                            </TouchableOpacity>
                        </View>
                        <Text className="text-xs text-white/30">
                            {vinylMode ? "Pitch drops with speed (Authentic)" : "Pitch stays constant (Time Stretch)"}
                        </Text>
                    </View>
                </View>
            </ScrollView>
        </View>
    );
}
