# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

## Kotlin Serialization
# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclasseswithmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclasseswithmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclasseswithmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-dontwarn javax.servlet.ServletContainerInitializer
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE
-dontwarn org.slf4j.impl.StaticLoggerBinder

## Rules for NewPipeExtractor
-keep class org.schabi.newpipe.extractor.services.youtube.protos.** { *; }
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

## Logging (does not affect Timber)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    ## Leave in release builds
    #public static int i(...);
    #public static int w(...);
    #public static int e(...);
}

# Generated automatically by the Android Gradle plugin.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# Keep all classes within the kuromoji package
-keep class com.atilika.kuromoji.** { *; }

## Queue Persistence Rules
# Keep queue-related classes to prevent serialization issues in release builds
-keep class com.metrolist.music.models.PersistQueue { *; }
-keep class com.metrolist.music.models.PersistPlayerState { *; }
-keep class com.metrolist.music.models.QueueData { *; }
-keep class com.metrolist.music.models.QueueType { *; }
-keep class com.metrolist.music.playback.queues.** { *; }

# Keep serialization methods for queue persistence
-keepclassmembers class * implements java.io.Serializable {
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

## UCrop Rules
-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }

## Home Screen - Quick Picks & Keep Listening
# Keep all database entities completely (Room needs them for deserialization)
-keep class com.metrolist.music.db.entities.** { *; }

# Keep LocalItem and all subclasses for home screen keep listening section
-keep class com.metrolist.music.db.entities.LocalItem { *; }
-keep class com.metrolist.music.db.entities.LocalItem$* { *; }

# Keep all relation and embedded classes
-keep class com.metrolist.music.db.entities.Song { *; }
-keep class com.metrolist.music.db.entities.Album { *; }
-keep class com.metrolist.music.db.entities.Artist { *; }
-keep class com.metrolist.music.db.entities.Playlist { *; }

# Keep data models needed for keep listening
-keep class com.metrolist.music.db.entities.SongEntity { *; }
-keep class com.metrolist.music.db.entities.AlbumEntity { *; }
-keep class com.metrolist.music.db.entities.ArtistEntity { *; }
-keep class com.metrolist.music.db.entities.PlaylistEntity { *; }

# Keep HomeViewModel to prevent stripping of observable state flows
-keep class com.metrolist.music.viewmodels.HomeViewModel { *; }

# Keep SyncUtils and WhitelistFetcher for whitelist functionality
-keep class com.metrolist.music.utils.SyncUtils { *; }
-keep class com.metrolist.music.utils.WhitelistFetcher { *; }
-keep class com.metrolist.music.utils.WhitelistFetcher$* { *; }

# Keep JSON parsing classes needed for whitelist sync
-keep class org.json.** { *; }
-dontwarn org.json.**

# Keep Ktor HTTP client classes and methods
-keep class io.ktor.client.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep DataStore and preferences for settings access
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.preferences.** { *; }

# Keep ArtistWhitelistEntity for database operations
-keep class com.metrolist.music.db.entities.ArtistWhitelistEntity { *; }
-keep class com.metrolist.music.db.entities.ArtistWhitelistEntity$* { *; }

# Keep Room generated classes and annotations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keepattributes *Annotation*

# Keep Room relationship classes
-keep class * extends androidx.room.DatabaseViewFtsOptions { *; }
-keep @androidx.room.Embedded class * { *; }
-keep @androidx.room.Relation class * { *; }

# Preserve constructor parameter names and annotations for Room
-keepclasseswithmembernames class * {
    @androidx.room.Embedded <methods>;
}
-keepclasseswithmembernames class * {
    @androidx.room.Relation <methods>;
}
