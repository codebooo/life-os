# LifeOS release keeps (§9.4)

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.lifeos.**$$serializer { *; }
-keepclassmembers class com.lifeos.** { *** Companion; }
-keepclasseswithmembers class com.lifeos.** { kotlinx.serialization.KSerializer serializer(...); }

# Jakarta Mail service loading
-keep class jakarta.mail.** { *; }
-keep class org.eclipse.angus.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn org.eclipse.angus.**

# Tink crypto registry
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# MediaPipe / ML Kit native bridges
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mediapipe.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Markdown renderer
-keep class com.mikepenz.markdown.** { *; }
