package it.marcolipparini.sfide.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SfideDao {

    @Query("SELECT * FROM templates ORDER BY updatedAt DESC")
    fun templates(): Flow<List<RoomTemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun template(id: String): RoomTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: RoomTemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("SELECT * FROM history ORDER BY playedAtEpochMs DESC")
    fun history(): Flow<List<MatchResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: MatchResultEntity)
}
