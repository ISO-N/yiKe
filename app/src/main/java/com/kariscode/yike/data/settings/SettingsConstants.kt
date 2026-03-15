package com.kariscode.yike.data.settings

/**
 * schemaVersion 需要和 Room 版本区分管理；
 * 之所以保存在本地设置中，是为了在备份恢复或未来数据迁移时能快速识别当前数据结构代际。
 */
object SettingsConstants {
    const val SCHEMA_VERSION: Int = 1
}

