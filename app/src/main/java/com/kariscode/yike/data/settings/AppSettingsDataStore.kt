package com.kariscode.yike.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore delegate 必须集中定义为单例扩展属性，
 * 否则在不同位置重复创建会导致读写竞争与不可预测的持久化行为。
 */
val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_settings"
)

