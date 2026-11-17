#!/bin/sh
# Usage: shot.sh NAME  -> screencap, saved small to /o/out/audit/NAME.png
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
mkdir -p /o/out/audit
"$ADB" exec-out screencap -p > /o/out/audit/_raw.png
python -c "
from PIL import Image
im=Image.open('O:/out/audit/_raw.png').convert('RGB')
im.thumbnail((360,800)); im.save('O:/out/audit/$1.png')
"
