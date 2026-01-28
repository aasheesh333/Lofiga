import { View, Text, TouchableOpacity, Alert } from 'react-native';
import { useRouter } from 'expo-router';
import * as DocumentPicker from 'expo-document-picker';
import { useAudio } from '../context/AudioContext';
import { LinearGradient } from 'expo-linear-gradient';
import { MaterialIcons } from '@expo/vector-icons';

export default function HomeScreen() {
    const { loadAudio, isLoading } = useAudio();
    const router = useRouter();

    const pickSong = async () => {
        try {
            const result = await DocumentPicker.getDocumentAsync({
                type: 'audio/*',
                copyToCacheDirectory: true
            });

            if (result.canceled) return;

            const asset = result.assets[0];
            await loadAudio(asset.uri, asset.name);
            router.push('/player');
        } catch (e) {
            console.error(e);
            Alert.alert("Error", "Failed to pick song");
        }
    };

    return (
        <View className="flex-1 bg-[#191022] relative">
            <LinearGradient
                colors={['#191022', '#2e1065']}
                style={{ position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 }}
            />

            <View className="flex-1 items-center justify-center px-6">
                <View className="mb-12 items-center">
                    <View className="w-16 h-16 bg-primary rounded-full items-center justify-center mb-4 shadow-lg shadow-primary/50">
                        <MaterialIcons name="music-note" size={32} color="white" />
                    </View>
                    <Text className="text-white text-3xl font-bold">Select Audio</Text>
                    <Text className="text-white/60 text-center mt-2">
                        Choose a song from your device to transform it into Lofi.
                    </Text>
                </View>

                <TouchableOpacity
                    onPress={pickSong}
                    disabled={isLoading}
                    className="w-full max-w-xs bg-primary h-14 rounded-xl items-center justify-center flex-row gap-2 shadow-lg shadow-primary/30 active:scale-95 transition-transform"
                >
                    {isLoading ? (
                        <Text className="text-white font-bold text-lg">Loading...</Text>
                    ) : (
                        <>
                            <MaterialIcons name="folder-open" size={24} color="white" />
                            <Text className="text-white font-bold text-lg">Import File</Text>
                        </>
                    )}
                </TouchableOpacity>

                <Text className="text-white/30 text-xs mt-8">
                    Supports MP3, WAV, AAC
                </Text>
            </View>
        </View>
    );
}
