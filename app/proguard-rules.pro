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
