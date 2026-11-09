# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt

-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Keep PureData classes
-keep class org.puredata.** { *; }
-keepclassmembers class org.puredata.** { *; }

# Keep MIDI classes
-keep class com.noisepages.nettoyeur.midi.** { *; }
-dontwarn com.noisepages.nettoyeur.midi.**

# Ignore warnings about missing optional MIDI support
-dontwarn org.puredata.android.midi.**

