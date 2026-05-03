
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
    # Bumped to 35 for plugin compatibility
    if kotlin_dsl:
        # Handle both hardcoded values and flutter.compileSdkVersion references
        content = re.sub(r'compileSdk\s*=\s*(flutter\.compileSdkVersion|\d+)', 'compileSdk = 35', content)
        content = re.sub(r'minSdk\s*=\s*(flutter\.minSdkVersion|\d+)', 'minSdk = 24', content)
        content = re.sub(r'targetSdk\s*=\s*(flutter\.targetSdkVersion|\d+)', 'targetSdk = 35', content)
    else:
        content = re.sub(r'compileSdkVersion\s*(flutter\.compileSdkVersion|\d+)', 'compileSdkVersion 35', content)
        content = re.sub(r'minSdkVersion\s*(flutter\.minSdkVersion|\d+)', 'minSdkVersion 24', content)
        content = re.sub(r'targetSdkVersion\s*(flutter\.targetSdkVersion|\d+)', 'targetSdkVersion 35', content)

    # Check if signing config already exists
    if 'storeFile = file("../../release.keystore")' in content:
        print("Signing config already present. Skipping.")
    else:
        print("Injecting signing config into existing android block...")

        if kotlin_dsl:
            # Insert signingConfigs before buildTypes {
            content = content.replace(
                '    buildTypes {',
                '    signingConfigs {\n'
                '        create("release") {\n'
                '            storeFile = file("../../release.keystore")\n'
                '            storePassword = "lofiga2024"\n'
                '            keyAlias = "lofiga"\n'
                '            keyPassword = "lofiga2024"\n'
                '        }\n'
                '    }\n'
                '\n'
                '    buildTypes {'
            )
            # Replace the debug signing config in release build type with our release signing config
            content = content.replace(
                '            signingConfig = signingConfigs.getByName("debug")',
                '            signingConfig = signingConfigs.getByName("release")'
            )
        else:
            # Groovy version - same approach
            content = content.replace(
                '    buildTypes {',
                '    signingConfigs {\n'
                '        release {\n'
                '            storeFile file(\'../../release.keystore\')\n'
                '            storePassword \'lofiga2024\'\n'
                '            keyAlias \'lofiga\'\n'
                '            keyPassword \'lofiga2024\'\n'
                '        }\n'
                '    }\n'
                '\n'
                '    buildTypes {'
            )
            content = content.replace(
                '            signingConfig signingConfigs.debug',
                '            signingConfig signingConfigs.release'
            )

    with open(filename, 'w') as f:
        f.write(content)

    print(f"Successfully updated {filename}")

except Exception as e:
    print(f"Error configuring Android project: {e}")
    exit(1)
