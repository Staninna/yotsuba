# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.stan.yotsuba.**$$serializer { *; }
-keepclassmembers class dev.stan.yotsuba.** { *** Companion; }
-keepclasseswithmembers class dev.stan.yotsuba.** { kotlinx.serialization.KSerializer serializer(...); }
# Navigation type-safe routes look their argument types up by fully qualified name, so a
# renamed route or argument class fails at graph construction -- on launch. Keeping the
# package costs a few classes and removes a whole category of release-only crash.
-keep class dev.stan.yotsuba.navigation.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
