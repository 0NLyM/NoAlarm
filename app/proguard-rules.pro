# Glyph Matrix SDK di Nothing: comunica via AIDL con un servizio di sistema,
# quindi le sue classi (incluso il pacchetto interno IGlyphService) non vanno
# rinominate ne' rimosse anche se R8 non ne vede riferimenti diretti.
-dontwarn com.nothing.ketchum.**
-keep class com.nothing.ketchum.** { *; }
-keep class com.nothing.thirdparty.** { *; }
