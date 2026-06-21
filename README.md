# VoiceBoard 🎤

An Android keyboard (IME) with voice-first input. One tap to record, instant transcription, and AI-powered rephrasing — all inside any text field.

## Download

**[⬇ Download latest APK](https://github.com/jagajaga/voiceboard/releases/tag/latest)**

Direct `.apk` — no unzipping needed. Built automatically on every push to `main`.

---

## Features

### 🎤 Voice Dictation
Tap **Record** → speak → tap **Stop**. Transcribed text is inserted at the cursor.
Powered by OpenAI **gpt-4o-transcribe**.

### ✏️ AI Rephrase
Select any text in any app → **Rephrase selection** button appears.
Tap it → speak your instruction ("make it shorter", "translate to French", "more formal") → the selected text is replaced with the rewritten version.
Powered by OpenAI **gpt-5.5**.

### ⌫ Smart Delete
Deletes selected text if highlighted, otherwise backspaces one character.

### ⌨ Switch Keyboard
Instantly brings up the system keyboard picker to jump back to Gboard or any other IME.

---

## Setup

1. Install the APK (enable "Install from unknown sources" if prompted)
2. Open the **VoiceBoard** app:
   - Grant **Microphone** permission
   - Tap **Open Input Method Settings** → enable VoiceBoard
   - Paste your **OpenAI API key** → Save
3. In any text field, tap the keyboard globe/switch icon → select **VoiceBoard**

Your API key is stored on-device only (SharedPreferences). It is never compiled into the APK.

---

## Building locally

```bash
# Add your API key if you want to bake it in for testing (optional)
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Signing uses the keystore at `$KEYSTORE_PATH` (env var). Falls back to the debug keystore for local builds.

---

## Tech stack

| Layer | Detail |
|---|---|
| Platform | Android IME (`InputMethodService`), min SDK 26 (Android 8) |
| Language | Kotlin |
| Transcription | OpenAI `gpt-4o-transcribe` via `/v1/audio/transcriptions` |
| Rephrase | OpenAI `gpt-5.5` via `/v1/chat/completions` |
| Networking | OkHttp |
| Async | Kotlin Coroutines |
| CI | GitHub Actions → direct APK release |

---

## Color scheme

Matches the dark olive Gboard palette (`#141A12` background, `#3A4438` buttons, `#E8EDE3` text).
