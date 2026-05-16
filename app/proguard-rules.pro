# Add project specific ProGuard rules here.

# OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt
-dontwarn dagger.hilt.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**
