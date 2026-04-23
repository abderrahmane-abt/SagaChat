# NavScreens — sealed class routes must survive shrinking so Navigation
# can resolve destinations by route string at runtime.
-keep class com.dark.tool_neuron.model.NavScreens { *; }
-keep class com.dark.tool_neuron.model.NavScreens$* { *; }

# Kotlin serialization — keeps @Serializable class structure intact.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }

# Hilt — generated component classes must not be renamed.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# ai_sd — keep public API and JNI
-keep class com.dark.ai_sd.** { *; }

# Local native modules — explicit keeps to satisfy R8 whole-program analysis
-keep class com.dark.hxs.** { *; }
-keep class com.dark.hxs_encryptor.** { *; }
-keep class com.dark.download_manager.** { *; }

-dontwarn com.dark.hxs.**
-dontwarn com.dark.hxs_encryptor.**
-dontwarn com.dark.download_manager.**

# Release-mode log stripping — R8 treats these calls as having no side effects,
# deletes them, and drops the string arguments that would otherwise leak paths,
# keys, or diagnostics into logcat.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Obfuscate everything we own except the JNI surface kept above.
-repackageclasses ''
-allowaccessmodification
