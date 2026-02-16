
import os
import re

print("Running Android Configuration Script...")

# Determine DSL
kotlin_dsl = os.path.exists('android/app/build.gradle.kts')
filename = 'android/app/build.gradle.kts' if kotlin_dsl else 'android/app/build.gradle'

print(f"Targeting: {filename}")

try:
    with open(filename, 'r') as f:
        content = f.read()

    # Update SDK versions using direct replacement
    # Using raw strings for regex to avoid escaping issues
    if kotlin_dsl:
        content = re.sub(r'compileSdk\s*=\s*flutter.compileSdkVersion', 'compileSdk = 34', content)
        content = re.sub(r'minSdk\s*=\s*flutter.minSdkVersion', 'minSdk = 24', content)
        content = re.sub(r'targetSdk\s*=\s*flutter.targetSdkVersion', 'targetSdk = 34', content)
    else:
        content = re.sub(r'compileSdkVersion\s*flutter.compileSdkVersion', 'compileSdkVersion 34', content)
        content = re.sub(r'minSdkVersion\s*flutter.minSdkVersion', 'minSdkVersion 24', content)
        content = re.sub(r'targetSdkVersion\s*flutter.targetSdkVersion', 'targetSdkVersion 34', content)

    # Append Signing Config
    # Check if we already appended (basic check)
    if 'signingConfigs {' not in content or 'storeFile = file("../../release.keystore")' not in content:
        print("Appending signing config...")
        
        content_kotlin = '''
android {
    signingConfigs {
        create("release") {
            storeFile = file("../../release.keystore")
            storePassword = "lofiga2024"
            keyAlias = "lofiga"
            keyPassword = "lofiga2024"
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
'''
        content_groovy = '''
android {
    signingConfigs {
        release {
            storeFile file('../../release.keystore')
            storePassword 'lofiga2024'
            keyAlias 'lofiga'
            keyPassword 'lofiga2024'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
'''
        content += (content_kotlin if kotlin_dsl else content_groovy)
    else:
        print("Signing config already present (or partially present). Skipping append.")

    with open(filename, 'w') as f:
        f.write(content)
        
    print(f"Successfully updated {filename}")

except Exception as e:
    print(f"Error configuring Android project: {e}")
    exit(1)
