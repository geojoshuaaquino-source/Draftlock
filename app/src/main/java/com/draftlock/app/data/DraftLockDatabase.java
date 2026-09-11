package com.draftlock.app.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {AppRequirement.class, LockedApp.class, DailyRecord.class, LocalDocument.class},
        version = 2,
        exportSchema = false
)
public abstract class DraftLockDatabase extends RoomDatabase {
    public abstract DraftLockDao dao();

    private static volatile DraftLockDatabase INSTANCE;

    public static DraftLockDatabase get(Context context) {
        DraftLockDatabase instance = INSTANCE;
        if (instance != null) {
            return instance;
        }
        synchronized (DraftLockDatabase.class) {
            instance = INSTANCE;
            if (instance == null) {
                instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        DraftLockDatabase.class,
                        "draftlock.db"
                ).fallbackToDestructiveMigration().build();
                INSTANCE = instance;
            }
            return instance;
        }
    }
}
