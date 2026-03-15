package com.kariscode.yike.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.repository.AppSettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * DataStore 实现的目的不是“把 key-value 暴露给上层”，而是提供稳定的设置语义：
 * - 默认值可控
 * - 读取流可观察
 * - 写入动作集中，便于后续在写入后触发提醒重建等副作用
 */
class DataStoreAppSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : AppSettingsRepository {
    /**
     * 在 data 层做默认值兜底可以让 domain/ui 不关心 key 是否存在，
     * 从而把“空值语义”固定下来，减少边界分支。
     */
    override fun observeSettings(): Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw throwable
        }
        .map { prefs ->
            AppSettings(
                dailyReminderEnabled = prefs[Keys.dailyReminderEnabled] ?: false,
                dailyReminderHour = prefs[Keys.dailyReminderHour] ?: 20,
                dailyReminderMinute = prefs[Keys.dailyReminderMinute] ?: 0,
                schemaVersion = prefs[Keys.schemaVersion] ?: SettingsConstants.SCHEMA_VERSION,
                backupLastAt = prefs[Keys.backupLastAt]
            )
        }

    /**
     * 将写入集中在单点，后续若需要在“开启提醒”时同时做权限提示/重建任务，
     * 可以通过用例编排而不必修改 UI 层读写细节。
     */
    override suspend fun setDailyReminderEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.dailyReminderEnabled] = enabled }
    }

    /**
     * 时间参数在此处统一保存，避免设置页组件各自保存导致 hour/minute 不一致。
     */
    override suspend fun setDailyReminderTime(hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.dailyReminderHour] = hour
            prefs[Keys.dailyReminderMinute] = minute
        }
    }

    /**
     * schemaVersion 的写入接口保留在仓储中，是为了未来迁移流程能在同一抽象下更新设置，
     * 而不是让迁移代码依赖 DataStore key。
     */
    override suspend fun setSchemaVersion(schemaVersion: Int) {
        dataStore.edit { prefs -> prefs[Keys.schemaVersion] = schemaVersion }
    }

    /**
     * 允许写入 null 用 remove 表达“无最近备份”，可避免把 0 当成时间戳造成误导。
     */
    override suspend fun setBackupLastAt(epochMillis: Long?) {
        dataStore.edit { prefs ->
            if (epochMillis == null) prefs.remove(Keys.backupLastAt)
            else prefs[Keys.backupLastAt] = epochMillis
        }
    }

    private object Keys {
        val dailyReminderEnabled = booleanPreferencesKey("dailyReminderEnabled")
        val dailyReminderHour = intPreferencesKey("dailyReminderHour")
        val dailyReminderMinute = intPreferencesKey("dailyReminderMinute")
        val schemaVersion = intPreferencesKey("schemaVersion")
        val backupLastAt = longPreferencesKey("backupLastAt")
    }
}
