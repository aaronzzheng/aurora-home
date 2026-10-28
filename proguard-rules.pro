# R8 removes code Aurora Home never calls (most of the Kotlin standard library) and
# optimizes the rest. Names stay readable so crash logs from the TV make sense.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# WallpaperView finds the bundled pictures by reading R.drawable's field names.
-keep class home.aurora.R$drawable { public static <fields>; }
