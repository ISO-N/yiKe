package com.kariscode.yike.data.backup

/**
 * 备份格式版本必须显式常量化，原因是导入校验与兼容策略都依赖这个数值，
 * 若散落在序列化模型中容易在升级时遗漏，导致恢复行为不可预期。
 */
object BackupConstants {
    const val BACKUP_VERSION: Int = 1
}

