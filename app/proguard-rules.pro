# Firestore documents are mapped by hand (see FirestoreMapper), so no model
# classes need to be kept for reflection.

# Strip verbose/debug/info logging from release builds. Task content is never
# logged, but this removes the calls entirely as a second line of defence.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep line numbers for readable crash stack traces; hide original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Glance identifies widgets by their class (GlanceAppWidgetManager.getGlanceIds).
# Without this, R8 may merge look-alike widget classes into one, so updating one
# widget type repaints another (seen on device: Quick add showed the Focus widget).
-keep,allowobfuscation class * extends androidx.glance.appwidget.GlanceAppWidget
-keep,allowobfuscation class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
# Glance creates ActionCallbacks by reflection, so their no-argument constructor must survive.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
