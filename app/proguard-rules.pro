# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\AndroidSDK/tools/proguard/proguard-android.txt
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

# --- Gson (backup export/import + saved profiles) ---
# minifyEnabled was off until now, so none of this was ever exercised - turning R8 on without
# these rules would silently corrupt every exported backup and every saved profile, since Gson
# reads/writes these fields by name via reflection: R8 renaming or removing an unused-looking
# field (which is exactly what a data class field written only by Gson looks like to R8, since
# nothing in the Kotlin code itself "calls" it) breaks that round-trip. -keepclassmembers instead
# of a blanket -keep on Gson's own classes: this only protects the field names/types R8 could
# otherwise mangle, without disabling shrinking of Gson's unused internals.
-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Every data class that crosses a Gson fromJson/toJson boundary (ExportData directly; the other four
# both directly, via BloodMoonRepository's per-field gson.toJson/fromJson calls, and indirectly, as
# ExportData's own nested fields) plus the enums their fields hold. IMPORTANT: everything in
# data.model.** is left out of R8 (names, fields and generic signatures). ProfileScheduleEntry was
# missing from the old list and, in the release APK, R8 erased the generic signature of "daysOfWeek:
# List<Int>" - Gson started reading the days as Double (1.0, 2.0...) and the app crashed with
# "ClassCastException: Double cannot be cast to Integer" when tapping the days of the week of a
# saved schedule (and `Int in List<Double>` never matched, so the schedule also never fired). With
# the whole package kept, a new model class is not forgotten again.
-keep class com.bloodmoon.app.data.model.** { *; }
-keepclassmembers class com.bloodmoon.app.data.repository.ExportData { *; }
-keepclassmembers enum com.bloodmoon.app.data.repository.FilterState { *; }

# --- Shizuku ---
# Talks to the Shizuku manager process over AIDL/Binder, which resolves interfaces and service
# classes by their exact runtime name - the same class-identity-by-string-name problem Gson has
# above, just via Binder transaction codes instead of JSON field names. rikka's own consumer
# ProGuard rules (bundled in the aar) cover most of this already; this project's own
# UserService (the one AIDL-exposed class actually defined in this app, launched by Shizuku by
# fully-qualified name rather than called directly from Kotlin) is kept explicitly since R8 has
# no way to know it's an entry point from outside this app's own call graph.
-keep class com.bloodmoon.app.shizuku.UserService { *; }
-keep class rikka.shizuku.** { *; }

# --- libsu (Root) ---
# Shells out to `su` and parses stdout - no reflection of this app's own classes, but the
# library's own consumer rules aren't always picked up depending on AGP/R8 version, so this is
# kept explicit rather than assumed.
-keep class com.topjohnwu.superuser.** { *; }

# --- Glance app widget ---
# BloodMoonWidgetProvider is only ever referenced by fully-qualified class name from the
# AndroidManifest (the OS instantiates it via reflection when the widget updates) and by
# FilterOverlayService.requestUpdate(), neither of which R8 can trace back from the manifest
# entry the way it does an in-code constructor call - without this the class looked unused and
# got stripped, silently breaking the widget entirely instead of just failing to update it.
-keep class com.bloodmoon.app.widget.BloodMoonWidgetProvider { *; }
