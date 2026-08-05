# Prototype application. Add protocol-adapter keep rules when required.
# Tuya Smart Life SDK 7.x
-keep class com.alibaba.fastjson.** { *; }
-dontwarn com.alibaba.fastjson.**
-keep class com.thingclips.smart.mqttclient.mqttv3.** { *; }
-dontwarn com.thingclips.smart.mqttclient.mqttv3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class okio.** { *; }
-dontwarn okio.**
-keep class com.thingclips.** { *; }
-dontwarn com.thingclips.**
-keep class chip.** { *; }
-dontwarn chip.**
-keep class com.gzl.smart.** { *; }
-dontwarn com.gzl.smart.**
