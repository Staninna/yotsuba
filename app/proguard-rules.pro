# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.stan.yotsuba.**$$serializer { *; }
-keepclassmembers class dev.stan.yotsuba.** { *** Companion; }
-keepclasseswithmembers class dev.stan.yotsuba.** { kotlinx.serialization.KSerializer serializer(...); }
# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
