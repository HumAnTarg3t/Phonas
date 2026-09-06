# SMBJ + BouncyCastle rely on reflection and generic signatures; keep them intact under R8.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class com.esotericsoftware.kryo.** { *; }
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn com.esotericsoftware.**
-dontwarn org.slf4j.**

# Tink (via androidx.security-crypto) and MBassador (via smbj) reference JVM-only classes that
# do not exist on Android. Without these, minifyReleaseWithR8 aborts and no release APK builds.
# net.engio.mbassy is a separate top-level package, so -dontwarn com.hierynomus.** misses it.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper
