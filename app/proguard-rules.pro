# R8 / ProGuard rules.
#
# Enable this only alongside real tests: minification renames classes, so a
# bug that hides behind reflection shows up as a ClassNotFoundException at
# runtime rather than a compile error. `assembleRelease lint testDebugUnitTest`
# covers the formulas; on-device smoke testing covers this list.

# Worker subclasses are instantiated by class name via reflection, so the
# class name and its (Context, WorkerParameters) constructor must survive.
-keep class * extends androidx.work.Worker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# WorkManager instantiates ListenableWorker.Factory implementations reflectively.
-keep class * implements androidx.work.ListenableWorkerFactory { <init>(); }

# SQLiteOpenHelper subclasses are created from SQLiteOpenHelper's own lookup,
# and this project's helpers are package-private, so keep names and ctors.
-keep class * extends android.database.sqlite.SQLiteOpenHelper { <init>(...); }

# Layout inflation resolves custom views by their fully qualified name.
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# HiMiuix widgets are referenced from Java only, but they extend View and are
# inflated in places; keeping their two-attribute ctor avoids surprises.
-keep public class himiuix.** { public <init>(android.content.Context, android.util.AttributeSet); }

# org.json is used throughout instead of generated models. Field names are
# part of the on-disk backup format, so restore must keep matching getters.
-keepclassmembers class org.json.** { *; }

# Keep line numbers so a release crash report is still readable, but hide the
# original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile