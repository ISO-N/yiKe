package com.kariscode.yike.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.model.toSyncedAppSettings
import com.kariscode.yike.domain.repository.AppSettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore 实现的目的不是“把 key-value 暴露给上层”，而是提供稳定的设置语义：
 * - 默认值可控
 * - 读取流可观察
 * - 写入动作集中，便于后续在写入后触发提醒重建等副作用
 */
class DataStoreAppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val timeProvider: TimeProvider,
    private val syncChangeRecorder: LanSyncChangeRecorder? = null
) : AppSettingsRepository {
    /**
     * 读取 DataStore 的容错和默认值解释必须保持单一入口，
     * 否则首帧快照与持续订阅很容易因为复制粘贴而出现语义漂移。
     */
    private val settingsFlow: Flow<AppSettings> = dataStore.data
        .recoverReadFailures()
        .map { prefs -> prefs.toAppSettings() }

    /**
     * 在 data 层做默认值兜底可以让 domain/ui 不关心 key 是否存在，
     * 从而把“空值语义”固定下来，减少边界分支。
     */
    override fun observeSettings(): Flow<AppSettings> = settingsFlow

    /**
     * 快照读取复用与订阅流相同的默认值口径，是为了避免“首帧读取”和“后续观察”得到不同配置解释。
     */
    override suspend fun getSettings(): AppSettings = settingsFlow.first()

    /**
     * 将写入集中在单点，后续若需要在“开启提醒”时同时做权限提示/重建任务，
     * 可以通过用例编排而不必修改 UI 层读写细节。
     */
    override suspend fun setDailyReminderEnabled(enabled: Boolean) {
        updateSyncedSettings { prefs ->
            prefs[Keys.dailyReminderEnabled] = enabled
        }
    }

    /**
     * 时间参数在此处统一保存，避免设置页组件各自保存导致 hour/minute 不一致。
     */
    override suspend fun setDailyReminderTime(hour: Int, minute: Int) {
        updateSyncedSettings { prefs ->
            prefs[Keys.dailyReminderHour] = hour
            prefs[Keys.dailyReminderMinute] = minute
        }
    }

    /**
     * 整份设置快照通过一次 edit 落盘，能保证备份恢复时不会把同一份状态拆成多次磁盘写入。
     */
    override suspend fun setSettings(settings: AppSettings) {
        updateSyncedSettings { prefs ->
            writeSettingsSnapshot(prefs = prefs, settings = settings)
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

    /**
     * 主题模式在仓储层统一转成稳定字符串，是为了让 DataStore 继续保持简单 key-value 结构，
     * 同时不把枚举序列化策略泄漏给页面层。
     */
    override suspend fun setThemeMode(mode: ThemeMode) {
        updateSyncedSettings { prefs -> prefs[Keys.themeMode] = mode.storageValue }
    }

    /**
     * 同步层回放远端设置时需要避免再次写入本地 journal，
     * 因此单独开放无记录入口能阻止“远端设置 -> 本地 journal -> 下次又推回远端”的回声。
     */
    suspend fun applySyncedSettingsWithoutRecording(settings: AppSettings) {
        dataStore.edit { prefs ->
            writeSettingsSnapshot(prefs = prefs, settings = settings)
        }
    }

    private object Keys {
        val dailyReminderEnabled = booleanPreferencesKey("dailyReminderEnabled")
        val dailyReminderHour = intPreferencesKey("dailyReminderHour")
        val dailyReminderMinute = intPreferencesKey("dailyReminderMinute")
        val schemaVersion = intPreferencesKey("schemaVersion")
        val backupLastAt = longPreferencesKey("backupLastAt")
        val themeMode = stringPreferencesKey("themeMode")
        val streakAchievementUnlocksJson = stringPreferencesKey("streakAchievementUnlocks")
    }

    /**
     * 整份设置快照统一经由同一写入入口，是为了让本地修改、恢复和同步回放共享完全一致的落盘语义，
     * 避免某条路径未来新增字段后漏写或漏删可空值。
     */
    private fun writeSettingsSnapshot(
        prefs: MutablePreferences,
        settings: AppSettings
    ) {
        prefs[Keys.dailyReminderEnabled] = settings.dailyReminderEnabled
        prefs[Keys.dailyReminderHour] = settings.dailyReminderHour
        prefs[Keys.dailyReminderMinute] = settings.dailyReminderMinute
        prefs[Keys.schemaVersion] = settings.schemaVersion
        if (settings.backupLastAt == null) {
            prefs.remove(Keys.backupLastAt)
        } else {
            prefs[Keys.backupLastAt] = settings.backupLastAt
        }
        prefs[Keys.themeMode] = settings.themeMode.storageValue
        prefs[Keys.streakAchievementUnlocksJson] = encodeStreakAchievementUnlocks(settings.streakAchievementUnlocks)
    }

    /**
     * 设置快照映射收敛为单点后，订阅读取和单次读取都能共享同一默认值解释，避免字段补漏。
     */
    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        dailyReminderEnabled = this[Keys.dailyReminderEnabled] ?: false,
        dailyReminderHour = this[Keys.dailyReminderHour] ?: 20,
        dailyReminderMinute = this[Keys.dailyReminderMinute] ?: 0,
        schemaVersion = this[Keys.schemaVersion] ?: SettingsConstants.SCHEMA_VERSION,
        backupLastAt = this[Keys.backupLastAt],
        themeMode = ThemeMode.fromStorageValue(this[Keys.themeMode]),
        streakAchievementUnlocks = this[Keys.streakAchievementUnlocksJson]?.let(::decodeStreakAchievementUnlocks)
            ?: emptyList()
    )

    /**
     * 只对 IO 异常回退空配置，是为了保留 DataStore 的“损坏可恢复、逻辑错误继续抛出”边界。
     */
    private fun Flow<Preferences>.recoverReadFailures(): Flow<Preferences> = catch { throwable ->
        if (throwable is IOException) emit(emptyPreferences()) else throw throwable
    }

    /**
     * 可同步字段的写入统一走同一模板，是为了既保留按字段 edit 的并发安全性，
     * 又避免每个 setter 都在 edit 后再额外读取一次 DataStore。
     */
    private suspend fun updateSyncedSettings(
        update: (MutablePreferences) -> Unit
    ) {
        val before = getSettings()
        var current = before
        dataStore.edit { prefs ->
            update(prefs)
            current = prefs.toAppSettings()
        }
        recordSyncedSettingsChange(previous = before, current = current)
    }

    /**
     * 只有跨设备可见字段真的发生变化时才写 journal，
     * 是为了避免最近备份时间或 schemaVersion 这类本地技术字段把同步流水刷得过于嘈杂。
     */
    private suspend fun recordSyncedSettingsChange(
        previous: AppSettings,
        current: AppSettings
    ) {
        val previousSynced = previous.toSyncedAppSettings()
        val currentSynced = current.toSyncedAppSettings()
        if (previousSynced == currentSynced) {
            return
        }
        syncChangeRecorder?.recordSettingsUpsert(
            settings = currentSynced,
            modifiedAt = timeProvider.nowEpochMillis()
        )
    }
}

