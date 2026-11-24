# Continuous Offline Speech Recognition

A small Kotlin + Jetpack Compose Android app that keeps listening and writes what you say into a result section.

## How it works

- `ContinuousSpeechRecognizer` wraps Android's `SpeechRecognizer` and restarts it after every utterance, silence timeout or recoverable error, so recognition keeps running until you tap **Stop**.
- **Offline:** on Android 12+ it uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` when the device supports it. Otherwise it uses the default recognizer with `EXTRA_PREFER_OFFLINE`. To work offline, the language pack for your device language must be downloaded (Google app → Settings → Voice → Offline speech recognition).
- Partial results show live in italics. Finished phrases are added to the transcript. **Copy** puts the transcript on the clipboard, and **Clear** resets it.

## Build

```sh
./gradlew installDebug
```

Requires JDK 17+ (Android Studio's bundled JBR works), compileSdk 37, minSdk 24.
