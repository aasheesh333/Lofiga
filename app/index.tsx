import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { LinearGradient } from 'expo-linear-gradient';
import Animated, { useAnimatedStyle, useSharedValue, withRepeat, withTiming, Easing } from 'react-native-reanimated';
import { useEffect } from 'react';

export default function SplashScreen() {
    const router = useRouter();
    const rotation = useSharedValue(0);

    useEffect(() => {
        rotation.value = withRepeat(withTiming(360, { duration: 5000, easing: Easing.linear }), -1);

        const timer = setTimeout(() => {
            router.replace('/home');
        }, 3000);
        return () => clearTimeout(timer);
    }, []);

    const animatedStyle = useAnimatedStyle(() => {
        return {
            transform: [{ rotateZ: `${rotation.value}deg` }]
        };
    });

    return (
        <View className="flex-1 items-center justify-center relative bg-[#191022]">
           <LinearGradient
             colors={['#0f172a', '#191022', '#2e1065']}
             style={{ position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 }}
           />

           {/* Glow (Simulated) */}
           <View className="absolute w-[300px] h-[300px] bg-primary opacity-20 rounded-full" style={{ transform: [{ scale: 2 }] }} />

           {/* Vinyl */}
           <Animated.View style={[animatedStyle, { width: 256, height: 256, borderRadius: 128, backgroundColor: '#121212', alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: 'rgba(255,255,255,0.1)' }]}>
              {/* Grooves */}
              <View className="absolute w-full h-full rounded-full border-4 border-white/5 opacity-20" />
              <View className="absolute w-[200px] h-[200px] rounded-full border-2 border-white/5 opacity-20" />
              <View className="absolute w-[150px] h-[150px] rounded-full border-2 border-white/5 opacity-20" />

              {/* Label */}
              <View className="w-24 h-24 rounded-full bg-primary items-center justify-center border border-white/10">
                 <View className="w-3 h-3 bg-black rounded-full shadow-inner" />
              </View>
           </Animated.View>

           <View className="items-center mt-12">
                <Text className="text-white text-5xl font-bold tracking-tight">Lofiga</Text>
                <Text className="text-white/60 text-lg mt-2 tracking-wide">Turn Any Song Into Lofi</Text>
           </View>

           <View className="absolute bottom-12 items-center gap-4">
                <Text className="text-white/30 text-xs tracking-widest uppercase">Version 1.0</Text>
           </View>
        </View>
    );
}
