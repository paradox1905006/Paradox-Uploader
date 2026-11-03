-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.dparadox.paradoxuploader.data.remote.**$$serializer { *; }
-keepclassmembers class com.dparadox.paradoxuploader.data.remote.** { *** Companion; }
-keepclasseswithmembers class com.dparadox.paradoxuploader.data.remote.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# History export/import files (@Serializable classes in data.repository): keep their serializers under R8.
-keep,includedescriptorclasses class com.dparadox.paradoxuploader.data.repository.**$$serializer { *; }
-keepclassmembers class com.dparadox.paradoxuploader.data.repository.** { *** Companion; }
-keepclasseswithmembers class com.dparadox.paradoxuploader.data.repository.** { kotlinx.serialization.KSerializer serializer(...); }

# TDLib (JNI calls into these classes by name)
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
