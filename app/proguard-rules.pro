# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.reaream.app.**$$serializer { *; }
-keepclassmembers class com.reaream.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.reaream.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Google Tink (used by EncryptedSharedPreferences)
-dontwarn com.google.errorprone.annotations.**
