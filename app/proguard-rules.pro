# Keep Gemini SDK models
-keep class com.google.firebase.ai.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class kotlinx.serialization.json.** { *; }
