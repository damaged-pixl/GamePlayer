# Flutter add-to-app: the engine and plugin registrant are accessed reflectively
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }
-keep class com.bee.arrowescape.** { *; }
-keepclasseswithmembers class * { native <methods>; }

# Optional Play Core hooks in the Flutter embedding (not used here)
-dontwarn com.google.android.play.core.**