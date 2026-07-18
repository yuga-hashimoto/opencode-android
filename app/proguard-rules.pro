# Project-specific R8 rules.
-keepattributes *Annotation*,Signature,SourceFile,LineNumberTable

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Compile-time-only Error Prone annotations referenced by Tink.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Gson reflective models returned by the OpenCode API.
-keep class com.google.gson.** { *; }
-keep class com.opencode.android.api.** { *; }

# Persisted JSON must retain stable field names across release builds and updates.
-keep class com.opencode.android.data.ConnectionProfile { *; }
-keep class com.opencode.android.runtime.LocalRuntimeMetadata { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
