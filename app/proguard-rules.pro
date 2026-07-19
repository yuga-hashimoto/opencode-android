# Project-specific R8 rules.
-keepattributes *Annotation*, Signature, SourceFile, LineNumberTable, InnerClasses, EnclosingMethod

# === Gson ===
# Preserve all reflection infrastructure Gson depends on.
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Preserve generic signatures so TypeToken<List<T>> works after minification.
-keepattributes Signature

# === OpenCode API models ===
# The whole API/data/runtime model layer is (de)serialized reflectively via Gson,
# so keep classes, fields, constructors and sealed subclasses verbatim.
-keep class com.opencode.android.core.api.** { *; }
-keep class com.opencode.android.data.connection.** { *; }
-keep class com.opencode.android.data.repository.** { *; }
-keep class com.opencode.android.data.settings.** { *; }
-keep class com.opencode.android.runtime.** { *; }
-keep class com.opencode.android.runtime.local.** { *; }
-keep class com.opencode.android.runtime.remote.** { *; }
-keep class com.opencode.android.feature.chat.SessionHandoff* { *; }
-keep class com.opencode.android.feature.chat.SessionHandoffPackage { *; }
-keep class com.opencode.android.feature.assistant.WakeWordPackManifest { *; }
-keep class com.opencode.android.feature.assistant.InstalledWakeWordPack { *; }
-keep class com.opencode.android.feature.connection.OpenCodeNsdDiscovery* { *; }
-keep class com.opencode.android.feature.connection.DiscoveredOpenCodeService { *; }

# === OkHttp / Okio ===
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# === Compile-time-only annotations referenced by Tink (security-crypto) ===
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# === Kotlin metadata ===
-keep class kotlin.Metadata { *; }
-keepattributes Kotlin
