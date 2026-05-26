# HXD public API — keep for consumers of this module
-keep class com.moorixlabs.download_manager.HxdManager        { *; }
-keep class com.moorixlabs.download_manager.HxdOptions        { *; }
-keep class com.moorixlabs.download_manager.HxdState          { *; }
-keep class com.moorixlabs.download_manager.HxdStatus         { *; }
-keep class com.moorixlabs.download_manager.HxdService        { *; }

# JNI bridge — native method names must not be obfuscated
-keepclasseswithmembernames class com.moorixlabs.download_manager.HxdNative {
    native <methods>;
}
