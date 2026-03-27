# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.moblin.android.**$$serializer { *; }
-keepclassmembers class com.moblin.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.moblin.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
