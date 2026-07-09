# 默认 ProGuard 规则
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.moontv.app.**$$serializer { *; }
-keepclassmembers class com.moontv.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.moontv.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Conscrypt
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**
