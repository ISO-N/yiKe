package com.kariscode.yike.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity

/**
 * RoomDatabase 是 data 层的基础设施入口；
 * 将其集中在单一类型中能确保事务边界（评分提交/恢复导入）有可靠承载点。
 */
@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        QuestionEntity::class,
        ReviewRecordEntity::class
    ],
    version = DatabaseConstants.ROOM_SCHEMA_VERSION,
    exportSchema = true
)
abstract class YikeDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun questionDao(): QuestionDao
    abstract fun reviewRecordDao(): ReviewRecordDao
}

