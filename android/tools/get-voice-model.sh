#!/bin/sh
# Puts the offline speech model for "Wick" voice commands into the app's assets. It is about 37 MB, so it is kept out of
# git; run this once after cloning. Without it the app builds, and the voice switch says the model is missing.
set -e
cd "$(dirname "$0")/.."
NAME=vosk-model-small-en-in-0.4
DEST=app/src/main/assets/model-en-in
[ -f "$DEST/uuid" ] && { echo "Voice model already in $DEST"; exit 0; }
TMP=$(mktemp -d)
curl -fL -o "$TMP/model.zip" "https://alphacephei.com/vosk/models/$NAME.zip"
unzip -q "$TMP/model.zip" -d "$TMP"
rm -rf "$DEST" && mkdir -p "$(dirname "$DEST")"
mv "$TMP/$NAME" "$DEST"
# The library copies the model out of the app once, and copies it again whenever this id changes.
echo "$NAME" > "$DEST/uuid"
rm -rf "$TMP"
echo "Voice model in $DEST"
