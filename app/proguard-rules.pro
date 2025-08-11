# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
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

# Keep all data model classes (and their members) in the data package
# This prevents ProGuard/R8 from removing them in release builds,
# which is crucial for JSON serialization libraries like GSON or Moshi.
-keep class space.declared.weather.data.** { *; }
# This rule tells the R8 shrinker to keep all classes, interfaces,
# and their members (methods, fields) within your local data package.
# This is essential for Room databases to function correctly in release builds,
# as it prevents the removal of code that is used indirectly via reflection
# or code generation, such as DAOs, Entities, and TypeConverters.
-keep class space.declared.weather.data.local.** { *; }
-keep interface space.declared.weather.data.local.** { *; }

# It's also a good practice to keep all your data model classes
# that are parsed from JSON (like with Retrofit/Gson/Moshi) or used by Room.
# The @Keep annotation you added earlier is good, but this is a more
# comprehensive rule that guarantees they will not be removed.
-keep @androidx.annotation.Keep class * {*;}