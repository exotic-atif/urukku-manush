# Obfuscation & Optimization
-repackageclasses ''
-allowaccessmodification

# Strip verbose/debug/info Android logging from release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Third-party libraries
-dontwarn okhttp3.**
-dontwarn okio.**
