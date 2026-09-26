# kotlinx-serialization + Retrofit (converter does reflective serializer lookup)
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.aura.player.**$$serializer { *; }
-keepclassmembers class com.aura.player.** {
    *** Companion;
}
-keepclasseswithmembers class com.aura.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Retrofit generic signatures
-keepattributes AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
