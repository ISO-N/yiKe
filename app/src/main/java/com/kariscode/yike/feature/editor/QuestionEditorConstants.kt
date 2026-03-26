package com.kariscode.yike.feature.editor

/**
 * 编辑页常量集中定义，是为了让自动保存等时间语义在不同入口（输入防抖、返回补存、手动保存）之间保持一致，
 * 并避免未来调整节奏时遗漏某个分支导致体验不一致。
 */
internal object QuestionEditorConstants {
    const val AUTO_SAVE_DELAY_MILLIS: Long = 1_500L
}

