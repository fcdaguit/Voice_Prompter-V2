# Room generated implementations are referenced reflectively by name.
-keep class com.voiceprompter.data.AppDatabase_Impl { *; }

# Keep the foreground service and its action constants reachable from the manifest.
-keep class com.voiceprompter.tts.PlaybackService { *; }

# SAX parsing of word/document.xml uses the platform implementation; nothing to keep,
# but silence warnings from javax.xml stubs pulled in by the JDK stdlib.
-dontwarn javax.xml.**
