# Proguard rules for AuraPlay
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.auraplay.app.webview.WebMediaBridge { *; }
