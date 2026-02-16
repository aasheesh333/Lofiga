
import sys
import re
import os

print("Running iOS Configuration Script...")

path = 'ios/Runner.xcodeproj/project.pbxproj'

if not os.path.exists(path):
    print(f"Error: {path} not found!")
    sys.exit(1)

try:
    with open(path, 'r') as f:
        content = f.read()

    # Regex target: any bundle identifier assignment
    # Pattern: PRODUCT_BUNDLE_IDENTIFIER = <something>;
    pattern_bundle = r'(PRODUCT_BUNDLE_IDENTIFIER\s*=\s*[^;]+;)'
    
    if not re.search(pattern_bundle, content):
        print(f'Error: Pattern "{pattern_bundle}" not found in {path}')
        sys.exit(1)
    
    # Reverting to simple strategy: Just inject Team ID.
    # Keep CODE_SIGN_STYLE as is (Automatic assumed).
    # This satisfies Xcode validation, while --no-codesign skips actual signing.
    
    # We use raw string for regex replacement pattern to handle backreferences correctly.
    # \1 refers to the captured group (the original line).
    # We add indentation and the new line.
    replacement_bundle = r'''\1
				DEVELOPMENT_TEAM = XXXXXXXXXX;'''

    new_content = re.sub(pattern_bundle, replacement_bundle, content)

    with open(path, 'w') as f:
        f.write(new_content)
    
    print(f'Successfully patched {path} for no-codesign build.')

except Exception as e:
    print(f'Python script error: {e}')
    sys.exit(1)
