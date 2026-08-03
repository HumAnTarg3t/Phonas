# SMBJ + BouncyCastle rely on reflection and generic signatures; keep them intact under R8.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class com.esotericsoftware.kryo.** { *; }
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn com.esotericsoftware.**
-dontwarn org.slf4j.**
