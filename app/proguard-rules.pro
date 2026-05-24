# ═══════════════════════════════════════════════════════════
# Vivo Assistant — ProGuard / R8 Rules
# Codex KD Official
# ═══════════════════════════════════════════════════════════

# Keep application class
-keep class com.codexkd.vivoassistant.VivoApp { *; }

# ─── ROOM DATABASE ──────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface *
-keepclassmembers @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.migration.Migration

# ─── DATA MODELS (Gson serialization) ───────────────────────
-keep class com.codexkd.vivoassistant.models.** { *; }
-keepclassmembers class com.codexkd.vivoassistant.models.** { *; }

# ─── GSON ───────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ─── OKHTTP ─────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ─── ANDROID SERVICES ───────────────────────────────────────
# Keep Accessibility Service
-keep class com.codexkd.vivoassistant.accessibility.** { *; }

# Keep Notification Listener
-keep class com.codexkd.vivoassistant.services.NotificationAIService { *; }

# Keep Foreground Service
-keep class com.codexkd.vivoassistant.services.AssistantForegroundService { *; }

# Keep Overlay Service
-keep class com.codexkd.vivoassistant.overlay.OverlayService { *; }

# Keep Boot Receiver
-keep class com.codexkd.vivoassistant.receivers.BootReceiver { *; }

# ─── NAVIGATION COMPONENT ───────────────────────────────────
-keep class * extends androidx.navigation.NavArgs
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable

# ─── ML KIT (OCR) ───────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ─── MARKWON ────────────────────────────────────────────────
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# ─── LOTTIE ─────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ─── KOTLIN COROUTINES ──────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ─── VIEWBINDING ────────────────────────────────────────────
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static * bind(android.view.View);
}

# ─── DATASTORE ──────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ─── WORKMANAGER ────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ─── SUPPRESS WARNINGS ──────────────────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# ─── GENERAL ────────────────────────────────────────────────
# Keep source file names and line numbers in stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
