# VoiceBoard 🎤

Android IME (keyboard) with a single **Record** button. Tap → speak → transcribed text appears in any app's input field.

**Powered by:** OpenAI `gpt-4o-transcribe`

## How it works

1. Tap **🎤 Record** — starts recording via the microphone  
2. Tap **⏹ Stop** — sends audio to OpenAI Whisper API  
3. Transcribed text is pasted directly into the focused field

## Setup (after installing the APK)

1. Open the **VoiceBoard** app → grant microphone permission  
2. Tap **Open Input Method Settings** → enable **VoiceBoard**  
3. In any text field, switch to VoiceBoard via the keyboard icon  

## Building locally

```bash
echo "OPENAI_API_KEY=sk-..." >> local.properties
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## CI / GitHub Actions

Push to `main` → APK is built automatically.  
Download from the **Actions** tab → latest run → **voiceboard-debug** artifact.

Required secret in repo settings: `OPENAI_API_KEY`
