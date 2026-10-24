# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod

# Preserve BluetoothDevice reflection fallback for ELM327 RFCOMM channel 1
-keepclassmembers class android.bluetooth.BluetoothDevice {
    public android.bluetooth.BluetoothSocket createRfcommSocket(int);
}

# Preserve Services and BroadcastReceivers
-keep class com.example.data.obd.ObdTelemetryService { *; }
-keep class com.example.data.media.VisionMediaNotificationListenerService { *; }

# Preserve OBD and Telemetry data models
-keep class com.example.model.** { *; }
-keep class com.example.data.obd.ParsedObdMetrics { *; }
-keep class com.example.data.weather.LiveWeatherData { *; }

# OkHttp3 & Coroutines safe rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
