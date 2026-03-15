package com.kariscode.yike.data.local.db

/**
 * 将数据库版本号集中为常量的原因是：它不仅影响 Room migration，
 * 还会影响备份恢复与排障定位；集中管理能避免出现“代码里一处是 1、另一处是 2”的漂移。
 */
object DatabaseConstants {
    const val ROOM_SCHEMA_VERSION: Int = 1
}

