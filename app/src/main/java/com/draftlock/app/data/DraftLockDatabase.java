package com.draftlock.app.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {AppRequirement.class, LockedApp.class, DailyRecord.class, LocalDocument.class, SprintSession.class},
        version = 3,
        exportSchema = false
)
public abstract class DraftLockDatabase extends RoomDatabase {
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS sprint_sessions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER NOT NULL, plannedMinutes INTEGER NOT NULL, wordsAtStart INTEGER NOT NULL, wordsWritten INTEGER NOT NULL, completed INTEGER NOT NULL)");
        }
    };
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
                ).addMigrations(MIGRATION_2_3).build();
                INSTANCE = instance;
            }
            return instance;
        }
    }
}
