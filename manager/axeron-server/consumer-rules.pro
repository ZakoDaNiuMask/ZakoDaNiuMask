# The privileged server entry point is started from the native starter by class name, and the
# AIDL interface crosses the binder boundary, so both must survive R8.

-keep class com.zakodaniumask.manager.axeron.server.Server {
    public static void main(java.lang.String[]);
}

-keep class com.zakodaniumask.manager.axeron.server.AxeronService { *; }

-keep class com.zakodaniumask.manager.axeron.IAxeronService { *; }
-keep class com.zakodaniumask.manager.axeron.IAxeronService$Stub { *; }
-keep class com.zakodaniumask.manager.axeron.IAxeronService$Stub$Proxy { *; }

-dontwarn android.app.ActivityThread
-dontwarn android.os.SystemProperties
