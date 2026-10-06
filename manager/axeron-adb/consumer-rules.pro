-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Hidden platform APIs used by the ADB client (resolved by the platform at runtime).
-dontwarn com.android.org.conscrypt.**
-dontwarn org.conscrypt.**
-dontwarn android.os.SystemProperties
