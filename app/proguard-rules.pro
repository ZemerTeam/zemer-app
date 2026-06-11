# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# WebView JavaScript interfaces - MUST keep @JavascriptInterface methods
-keepclassmembers class com.jtech.zemer.utils.sabr.EjsNTransformSolver$SolverWebView {
    @android.webkit.JavascriptInterface public *;
}
-keepclassmembers class com.jtech.zemer.utils.cipher.CipherWebView {
    @android.webkit.JavascriptInterface public *;
}
-keep class com.jtech.zemer.utils.sabr.EjsNTransformSolver { *; }
-keep class com.jtech.zemer.utils.sabr.EjsNTransformSolver$SolverWebView { *; }
-keep class com.jtech.zemer.utils.cipher.CipherDeobfuscator { *; }
-keep class com.jtech.zemer.utils.cipher.CipherWebView { *; }
-keep class com.jtech.zemer.utils.cipher.PlayerJsFetcher { *; }

# Keep entire cipher and sabr packages (critical for stream playback)
-keep class com.jtech.zemer.utils.cipher.** { *; }
-keep class com.jtech.zemer.utils.sabr.** { *; }

# Keep coroutine continuation for WebView callbacks
-keepclassmembers class * {
    void resume(...);
    void resumeWithException(...);
}

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
-dontwarn com.google.re2j.**
-dontwarn org.jsoup.helper.Re2jRegex**

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

## Strip Timber logging in release
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
    public static void wtf(...);
    public static void log(int, java.lang.String, java.lang.String, java.lang.Throwable);
    public static void tag(java.lang.String);
}
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
    public void i(...);
    public void w(...);
    public void e(...);
    public void wtf(...);
    public void log(int, java.lang.String, java.lang.String, java.lang.Throwable);
}

## Queue Persistence Rules
# Keep queue-related classes to prevent serialization issues in release builds
-keep class com.jtech.zemer.models.PersistQueue { *; }
-keep class com.jtech.zemer.models.PersistPlayerState { *; }
-keep class com.jtech.zemer.models.QueueData { *; }
-keep class com.jtech.zemer.models.QueueType { *; }
-keep class com.jtech.zemer.playback.queues.** { *; }

## UCrop Rules
-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }

## Native Cover Art Library (Bento4 JNI)
-keep class com.jtech.zemer.utils.CoverArtNative { *; }

## Firebase and Auth Rules (for release build sync)
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.auth.** { *; }
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

## Firebase Authentication
-keep class com.google.firebase.auth.FirebaseAuth { *; }
-keep class com.google.firebase.auth.FirebaseUser { *; }
-keep class com.google.firebase.auth.AuthResult { *; }
-keep class com.google.firebase.auth.GoogleAuthProvider { *; }
-keep class com.google.android.gms.auth.api.signin.** { *; }

## Firebase Firestore
-keep class com.google.firebase.firestore.FirebaseFirestore { *; }
-keep class com.google.firebase.firestore.CollectionReference { *; }
-keep class com.google.firebase.firestore.DocumentReference { *; }
-keep class com.google.firebase.firestore.Query { *; }
-keep class com.google.firebase.firestore.DocumentSnapshot { *; }
-keep class com.google.firebase.firestore.QuerySnapshot { *; }

## App Sync Classes (prevent obfuscation)
-keep class com.jtech.zemer.sync.** { *; }
-keep class com.jtech.zemer.auth.** { *; }
-keep class com.jtech.zemer.utils.DeviceIdGenerator { *; }
-keep class com.jtech.zemer.utils.ContentFilterConfig { *; }
-keep class com.jtech.zemer.utils.ContentFilterState { *; }
-keep class com.jtech.zemer.sync.models.** { *; }

## Keep DataStore and Preferences classes
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.preferences.** { *; }
-dontwarn androidx.datastore.**

## Hilt and Dependency Injection (keep sync-related)
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.annotation.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <methods>;
}

## Keep enum classes used in sync
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

## Keep Parcelable implementations for sync models
-keep class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

## Keep custom annotations used in sync module
-keep @interface com.jtech.zemer.di.SyncDataStore
-keep @interface com.jtech.zemer.di.MainDataStore
-keep @interface dagger.BindsInstance
-keep @interface dagger.Provides
-keep @interface javax.inject.Singleton
-keep @interface javax.inject.Qualifier

## Keep Gson serialization for Firestore models
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

## Keep R8/ProGuard from optimizing critical sync methods
-keepclassmembers class com.jtech.zemer.sync.UserPreferencesRepository {
    public *;
}

-keepclassmembers class com.jtech.zemer.auth.UserAuthManager {
    public *;
}

-keepclassmembers class com.jtech.zemer.utils.DeviceIdGenerator {
    public *;
}

## Keep Firebase callback interfaces
-keep interface com.google.android.gms.tasks.OnSuccessListener
-keep interface com.google.android.gms.tasks.OnFailureListener
-keep interface com.google.android.gms.tasks.OnCompleteListener

## Reflection and serialization for Firestore
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.jtech.zemer.sync.models.**$$serializer { *; }
-keepclassmembers class com.jtech.zemer.sync.models.** {
    *** Companion;
}
-keepclasseswithmembers class com.jtech.zemer.sync.models.** {
    kotlinx.serialization.KSerializer serializer(...);
}

## Preserve debugging information for sync troubleshooting
-keepattributes SourceFile,LineNumberTable
-keepattributes LocalVariableTable
-keepattributes LocalVariableTypeTable

## Shizuku & hidden Android APIs for the self-update installer
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class dev.rikka.tools.refine.** { *; }
-keep class android.content.pm.IPackageManager { *; }
-keep class android.content.pm.IPackageManager$Stub { *; }
-keep class android.content.pm.IPackageInstaller { *; }
-keep class android.content.pm.IPackageInstaller$Stub { *; }
-keep class android.content.pm.IPackageInstallerSession { *; }
-keep class android.content.pm.IPackageInstallerSession$Stub { *; }
-keep class android.content.pm.PackageInstallerHidden { *; }
-keep class android.content.pm.PackageInstallerHidden$* { *; }
-keep class android.content.pm.PackageManagerHidden { *; }

## libsu for the root install method
-keep class com.topjohnwu.superuser.** { *; }
