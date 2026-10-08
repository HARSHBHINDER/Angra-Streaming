# RootEncoder uses reflection/native entry points in places; keep its packages intact in release.
-keep class com.pedro.** { *; }
-dontwarn com.pedro.**

# Tink (via androidx.security-crypto) references optional compile-only annotations not on the classpath.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.crypto.tink.**
