package com.kariscode.yike.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kariscode.yike.data.local.db.entity.SyncChangeEntity

/**
 * SyncChangeDao 只负责 journal 的追加和增量读取，
 * 是为了让同步流水保持“只增不改”的心智模型，降低恢复与排查难度。
 */
@Dao
interface SyncChangeDao {
    /**
     * 变更流水按顺序追加，是为了让 seq 继续承担“本机变更顺序”的单一事实来源。
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(change: SyncChangeEntity): Long

    /**
     * 批量读取某个游标之后的流水，是为了让上传和冲突预览都能围绕同一增量窗口工作。
     */
    @Query("SELECT * FROM sync_change WHERE seq > :afterSeq ORDER BY seq ASC")
    suspend fun listAfter(afterSeq: Long): List<SyncChangeEntity>

    /**
     * 只拿表头信息就能先做冲突预览，是为了避免在用户尚未确认前就传输完整实体内容。
     */
    @Query(
        "SELECT * FROM sync_change WHERE seq > :afterSeq ORDER BY seq ASC LIMIT :limit"
    )
    suspend fun listAfterLimited(afterSeq: Long, limit: Int): List<SyncChangeEntity>

    /**
     * 备份按时间窗口导出增量流水，是为了复用同一份 journal 而不额外引入第二套“变更快照”存储。
     */
    @Query("SELECT * FROM sync_change WHERE modifiedAt > :afterModifiedAt ORDER BY seq ASC")
    suspend fun listModifiedAfter(afterModifiedAt: Long): List<SyncChangeEntity>

    /**
     * 最新 seq 单独查询可以让 cursor 推进和心跳状态更新少带一批无关记录。
     */
    @Query("SELECT COALESCE(MAX(seq), 0) FROM sync_change")
    suspend fun findLatestSeq(): Long
}
