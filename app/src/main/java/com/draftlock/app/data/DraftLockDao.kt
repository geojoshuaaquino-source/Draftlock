package com.draftlock.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftLockDao {
    @Query("SELECT * FROM app_requirements ORDER BY id")
    fun observeRequirements(): Flow<List<AppRequirement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequirement(requirement: AppRequirement)

    @Query("DELETE FROM app_requirements WHERE id = :id")
    suspend fun deleteRequirement(id: Long)

    @Query("SELECT * FROM locked_apps ORDER BY displayName")
    fun observeLockedApps(): Flow<List<LockedApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLockedApp(app: LockedApp)

    @Query("DELETE FROM locked_apps WHERE packageName = :packageName")
    suspend fun deleteLockedApp(packageName: String)

    @Query("SELECT * FROM daily_records WHERE dayKey = :dayKey LIMIT 1")
    suspend fun getDay(dayKey: String): DailyRecord?

    @Query("SELECT * FROM daily_records ORDER BY dayKey DESC LIMIT 30")
    fun observeRecentDays(): Flow<List<DailyRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(record: DailyRecord)

    @Query("SELECT * FROM local_documents ORDER BY updatedAt DESC")
    fun observeLocalDocs(): kotlinx.coroutines.flow.Flow<List<LocalDocument>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalDoc(doc: LocalDocument): Long

    @Query("DELETE FROM local_documents WHERE id = :id")
    suspend fun deleteLocalDoc(id: Long)

    @Query("SELECT * FROM local_documents WHERE id = :id LIMIT 1")
    suspend fun getLocalDoc(id: Long): LocalDocument?
}
