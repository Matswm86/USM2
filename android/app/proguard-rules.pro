# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class no.mwmai.pcleague.** {
    *** Companion;
}
-keepclasseswithmembers class no.mwmai.pcleague.** {
    kotlinx.serialization.KSerializer serializer(...);
}
