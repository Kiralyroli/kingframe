# WorkManager creates its Room database reflectively; R8 full mode drops the
# generated no-arg constructor otherwise (crash at startup in release builds).
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
