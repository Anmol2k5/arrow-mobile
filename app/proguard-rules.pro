# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Keep Gemini SDK classes
-keep class com.google.ai.client.generativeai.** { *; }

# Keep data classes
-keep class com.clicky.accessibility.** { *; }
-keep class com.clicky.copilot.** { *; }
-keep class com.clicky.overlay.** { *; }
-keep class com.clicky.memory.** { *; }
