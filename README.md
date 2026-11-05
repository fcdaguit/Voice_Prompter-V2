# Voice Prompter

A lightweight offline Android teleprompter. It reads your script aloud with the
device's own text-to-speech engine and highlights the line being spoken, so you
can deliver a sermon, lesson or talk by listening rather than reading.

No account, no network, no cloud sync. Scripts live only on the device.

## Status

Scaffold. The architecture, build and CI are in place; expect rough edges until
it has been run on real hardware.

## Features

- Create, edit, duplicate, search and delete scripts, with auto-save
- Import `.txt` and `.docx` files
- Sentence-by-sentence playback with the current line highlighted
- Tap any line to jump to it
- Play / pause / previous / next, plus voice, speed and pitch control
- Keeps speaking with the screen off, with controls in a notification
- Bluetooth headset media buttons for play, pause and skip
- Light / dark theme, adjustable prompter text size and colours
- Remembers your last script and position

## Requirements

- Android 10 (API 29) or newer
- A text-to-speech engine with offline voice data installed
  (Settings → Accessibility → Text-to-speech output)

## Building

```bash
git clone <your-repo-url>
cd VoicePrompter
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

If `gradle/wrapper/gradle-wrapper.jar` is missing (it is a binary and may not be
committed yet), generate it once:

```bash
gradle wrapper --gradle-version 8.11.1
```

Opening the project in Android Studio does the same thing automatically. CI also
generates it on the fly if it is absent.

### Running tests

```bash
./gradlew testDebugUnitTest
```

`Sentences` and `DocxExtractor` are deliberately plain JVM code with no Android
dependencies, so the logic that is easiest to get wrong is covered by fast unit
tests rather than instrumentation.

## CI

- `.github/workflows/android-ci.yml` — runs unit tests and lint, builds a debug
  APK and uploads it as a build artifact, on every push and pull request.
- `.github/workflows/release.yml` — on a `v*` tag, builds a release APK and
  attaches it to a GitHub Release.

Release signing is optional. Add these repository secrets to get signed builds;
without them CI produces an unsigned release APK.

| Secret | Meaning |
| --- | --- |
| `KEYSTORE_BASE64` | Your keystore, base64-encoded |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

```bash
base64 -w0 release.jks   # the value for KEYSTORE_BASE64
```

## Architecture

```
ui/          Compose screens, ViewModels, theme
  MainActivity      hand-rolled navigation over four screens
  ScriptListScreen  list, search, import, duplicate, delete
  EditorScreen      title + body, auto-saving
  PrompterScreen    highlighted playback view
  SettingsScreen    voice, speed, pitch, theme, colours, controls
tts/
  Sentences         splits a script into spoken units
  TtsEngine         wraps android.speech.tts.TextToSpeech
  PlaybackController shared observable playback state
  PlaybackService   foreground service + MediaSession
data/
  Database          Room entity, DAO, database
  ScriptRepository  CRUD and position persistence
  UserPrefs         SharedPreferences-backed settings
  DocxExtractor     dependency-free .docx text extraction
  DocumentImporter  URI → editable script
```

### Deliberate choices

**No Hilt.** Four singletons live in `AppContainer`, constructed in
`VoicePrompterApp`. Skipping an annotation processor for DI keeps build times
and method count down.

**No Apache POI.** A `.docx` is a ZIP; `DocxExtractor` streams out
`word/document.xml` and SAX-parses the text runs using only JDK APIs. POI would
add several megabytes for formatting this app discards anyway.

**No navigation-compose.** Four screens and no deep links do not justify the
dependency; `MainActivity` holds a sealed `Screen` type and a back stack.

**SharedPreferences, not DataStore.** A dozen scalars, no extra dependency.

**Platform `MediaSession`, not media3.** Only media-button handling is needed,
and the platform API has been adequate for that since API 21.

### Known limitations

- **Volume-key section navigation only works while the prompter is on screen.**
  Volume keys are not delivered to a backgrounded app. With the screen off, use
  a Bluetooth headset's media buttons, which reach the `MediaSession`.
- **Presentation remotes that send page-up/page-down also require focus.** Most
  such remotes act as Bluetooth keyboards, and keyboard events only go to the
  foreground activity.
- **Imported `.docx` formatting is discarded** — text, paragraph breaks, line
  breaks and tabs only. Tables, images, headers and footnotes are dropped.
- **Dependency versions are conservative** and were chosen for being known-good
  rather than newest. Bump them in `gradle/libs.versions.toml` once you have a
  green build.

## Licence

Not yet chosen.
