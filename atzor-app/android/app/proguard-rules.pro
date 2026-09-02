# Atzor release rules — keep reflection-sensitive surfaces alive under R8.

# Compose / Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# DataStore / coroutines
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
  <fields>;
}

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
# Analytics references AdvertisingId when we intentionally exclude the ads-id lib.
-dontwarn com.google.android.gms.ads.identifier.**
-dontwarn com.google.android.gms.internal.measurement.**

# ZXing embedded scanner
-keep class com.journeyapps.** { *; }
-keep class com.google.zxing.** { *; }

# Accessibility / services registered in the manifest
-keep class app.atzor.service.** { *; }
-keep class app.atzor.widget.** { *; }
-keep class app.atzor.MainActivity { *; }
-keep class app.atzor.AtzorApp { *; }
-keep class app.atzor.ui.BlockActivity { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
