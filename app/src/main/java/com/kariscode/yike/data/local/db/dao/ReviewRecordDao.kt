package com.kariscode.yike.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * ReviewRecord 只允许追加写入，不允许编辑，
 * 这样才能保证复习历史可追溯，且备份恢复后的历史不会被 UI 操作意外篡改。
 */
@Dao
interface ReviewRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: ReviewRecordEntity): Long

    @Query("SELECT * FROM review_record WHERE questionId = :questionId ORDER BY reviewedAt DESC")
    fun observeByQuestion(questionId: String): Flow<List<ReviewRecordEntity>>

    /**
     * 备份导出必须保留完整历史记录，
     * 否则恢复后会丢失评分链路，影响调度排查与后续统计扩展。
     */
    @Query("SELECT * FROM review_record ORDER BY reviewedAt ASC")
    suspend fun listAll(): List<ReviewRecordEntity>

    /**
     * 恢复导入需要批量重建复习历史，因此必须提供批量写入口以减少事务中的样板代码。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ReviewRecordEntity>): List<Long>

    /**
     * 清空复习记录是全量覆盖恢复的必要前置动作，
     * 否则旧历史会与新备份混杂，破坏“恢复结果完全等于备份”的语义。
     */
    @Query("DELETE FROM review_record")
    suspend fun clearAll(): Int
}
