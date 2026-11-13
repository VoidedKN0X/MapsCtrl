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
