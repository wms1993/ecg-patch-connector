# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\eclipse\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}


-dontshrink
-ignorewarnings
-dontwarn

-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

-keep class com.proton.ecgpatch.connector.EcgPatchManager {
    public *;
}

-keep class com.proton.ecgpatch.connector.bean.AlgorithmResult {
    public *;
}

-keep class com.proton.ecgpatch.connector.callback.** {
     *;
}

-keep class com.proton.ecgpatch.connector.utils.AppUtils {
     *;
}
-keep class com.proton.ecgpatch.connector.utils.BleUtils {
     *;
}
-keep class com.proton.ecgpatch.connector.data.parse.IBleDataParse {
     *;
}
-keep class com.proton.ecgpatch.connector.data.uuid.IDeviceUUID {
     *;
}
-keep class com.proton.ecgpatch.connector.utils.PatchFirmwareUpdateManager {
    public *;
}
-keep class com.proton.ecgpatch.connector.utils.PatchFirmwareUpdateManager$OnFirewareUpdateListener {
     *;
}
-keep class no.nordicsemi.android.dfu.** { *; }