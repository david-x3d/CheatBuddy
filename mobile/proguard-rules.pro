# kotlinx.serialization: keep generated serializers of shared models.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.cheatbuddy.shared.**$$serializer { *; }
-keepclassmembers class com.cheatbuddy.shared.** { *** Companion; }
-keepclasseswithmembers class com.cheatbuddy.shared.** { kotlinx.serialization.KSerializer serializer(...); }
