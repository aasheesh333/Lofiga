import "../global.css";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { AudioProvider } from "../context/AudioContext";
import { View } from "react-native";

export default function Layout() {
  return (
    <AudioProvider>
      <StatusBar style="light" />
      <View style={{ flex: 1, backgroundColor: '#191022' }}>
        <Stack
            screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: '#191022' },
            animation: 'fade'
            }}
        />
      </View>
    </AudioProvider>
  );
}
