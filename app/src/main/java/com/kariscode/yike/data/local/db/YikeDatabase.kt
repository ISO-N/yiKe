package com.kariscode.yike.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.AutoMigration
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.QuestionSearchTokenDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.local.db.dao.SyncChangeDao
import com.kariscode.yike.data.local.db.dao.SyncPeerCursorDao
import com.kariscode.yike.data.local.db.dao.SyncPeerDao
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.QuestionSearchTokenEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.data.local.db.entity.SyncChangeEntity
import com.kariscode.yike.data.local.db.entity.SyncPeerCursorEntity
import com.kariscode.yike.data.local.db.entity.SyncPeerEntity

/**
 * RoomDatabase 是 data 层的基础设施入口；
 * 将其集中在单一类型中能确保事务边界（评分提交/恢复导入）有可靠承载点。
 */
@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        QuestionEntity::class,
        QuestionSearchTokenEntity::class,
        ReviewRecordEntity::class,
        SyncChangeEntity::class,
        SyncPeerEntity::class,
        SyncPeerCursorEntity::class
    ],
    version = DatabaseConstants.ROOM_SCHEMA_VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5)
    ]
)
abstract class YikeDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun questionDao(): QuestionDao
    abstract fun questionSearchTokenDao(): QuestionSearchTokenDao
    abstract fun reviewRecordDao(): ReviewRecordDao
    abstract fun syncChangeDao(): SyncChangeDao
    abstract fun syncPeerDao(): SyncPeerDao
    abstract fun syncPeerCursorDao(): SyncPeerCursorDao
}

