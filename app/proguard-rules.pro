# kotlinx.serialization models (library ships its own rules; these keep saved places and routes intact).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class com.vknxworks.mapsctrl.**$$serializer { *; }
-keepclassmembers class com.vknxworks.mapsctrl.** {
    *** Companion;
}
-keepclasseswithmembers class com.vknxworks.mapsctrl.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Mapsforge reads its render theme with XmlPullParser and creates instructions by name; keep it whole.
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**
# AndroidSVG draws the theme's SVG symbols.
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**
# BRouter loads its profile expressions and model classes reflectively (StdModel, KinematicModel).
-keep class btools.** { *; }

# Google-free: Guava's listenablefuture jar is excluded from the build (see app/build.gradle.kts).
# AndroidX's concurrent-futures still names its interface in classes MapsCtrl never uses.
-dontwarn com.google.common.util.concurrent.ListenableFuture

# No logging at all in release builds: MapsCtrl's own code doesn't log, and any Log call that sneaks in
# later (or one in a library) is stripped.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}
