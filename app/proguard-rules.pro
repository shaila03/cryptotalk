# Add project specific ProGuard rules here.
# PROMPT 7 / CLEANUP 2 & 5: Full security hardening ProGuard rules

# ============================================================
# KEEP: Crypto package — never obfuscate encryption code
# ============================================================
-keep class com.cryptotalk.app.crypto.** { *; }
-keepclassmembers class com.cryptotalk.app.crypto.** { *; }

# ============================================================
# KEEP: Data models — required for Firestore serialization
# ============================================================
-keep class com.cryptotalk.app.data.model.** { *; }
-keepclassmembers class com.cryptotalk.app.data.model.** { *; }

# ============================================================
# KEEP: Firebase — Firestore, Auth, FCM, and Analytics
# ============================================================
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep interface com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ============================================================
# KEEP: Kotlin serialization and reflection
# ============================================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ============================================================
# KEEP: Gson serialization (used for local cache payloads)
# ============================================================
-keep class com.google.gson.** { *; }
-keep class com.google.common.reflect.TypeToken { *; }
-keep class * extends com.google.common.reflect.TypeToken

# ============================================================
# KEEP: Room database entities and DAOs
# ============================================================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# ============================================================
# CLEANUP 5: Strip ALL debug logs from release APK entirely
# assumenosideeffects tells R8 these calls have no effect and removes them
# ============================================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ============================================================
# Line numbers for crash reporting
# ============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Coroutines
# ============================================================
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ============================================================
# SQLCipher (encrypted local database)
# ============================================================
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# ============================================================
# AndroidX Biometric
# ============================================================
-keep class androidx.biometric.** { *; }

# ============================================================
# AndroidX Work Manager
# ============================================================
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }