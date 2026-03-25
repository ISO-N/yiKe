package com.kariscode.yike.core.message

/**
 * 统一管理应用中的错误消息和提示文本，
 * 避免在多处硬编码导致维护困难和内容不一致。
 */
object ErrorMessages {
    const val LOAD_FAILED = "加载失败"
    const val RETRY_LATER = "请稍后重试"
    const val SAVE_FAILED = "保存失败，请稍后重试"
    const val DRAFT_SAVE_FAILED = "草稿保存失败，请稍后重试"
    const val DELETE_FAILED = "删除失败，请稍后重试"
    const val UPDATE_FAILED = "更新失败，请稍后重试"

    // 通用页面加载错误
    const val HOME_LOAD_FAILED = "首页加载失败"
    const val DECK_LIST_LOAD_FAILED = "卡组列表加载失败"
    const val CARD_LIST_LOAD_FAILED = "卡片列表加载失败"
    const val ANALYTICS_LOAD_FAILED = "统计页加载失败"
    const val PREVIEW_LOAD_FAILED = "今日预览加载失败"
    const val SEARCH_LOAD_FAILED = "搜索页加载失败"
    const val REVIEW_LOAD_FAILED = "加载失败，请重试"
    const val SETTINGS_SAVE_FAILED = "设置保存失败，请稍后重试"

    // 编辑器相关
    const val NAME_REQUIRED = "名称不能为空"
    const val INTERVAL_STEP_COUNT_INVALID = "间隔次数需为 1 到 8 之间的整数"
    const val TITLE_REQUIRED = "标题不能为空"
    const val QUESTION_CONTENT_REQUIRED = "题面不能为空"
    const val ANSWER_CONTENT_REQUIRED = "答案不能为空"
    const val VALIDATION_ERROR = "请修正校验错误后再保存"
    const val CARD_NOT_FOUND = "卡片不存在或加载失败"

    // 备份恢复
    const val BACKUP_EXPORT_FAILED = "导出失败，请重试"
    const val BACKUP_RESTORE_FAILED = "恢复失败，当前数据未被修改"

    // 复习
    const val REVIEW_RECORD_FAILED = "记录失败，请重试"
    const val REVIEW_SUBMIT_FAILED = "评分提交失败"

    // 搜索
    const val SEARCH_FAILED = "搜索失败，请稍后重试"
}

/**
 * 统一管理应用中的成功提示文本，
 * 避免多个页面各自维护相似文案导致口径逐渐漂移。
 */
object SuccessMessages {
    const val SAVED = "已保存"
    const val CARD_CREATED = "卡片已创建"
    const val CARD_UPDATED = "卡片已更新"
    const val DECK_CREATED = "卡组已创建"
    const val DECK_UPDATED = "卡组已更新"
    const val DRAFT_SAVED = "草稿已保存到本机"
    const val DRAFT_RESTORED = "已恢复上次草稿"
    const val DRAFT_CORRUPTED_RESET = "草稿异常，已恢复正式内容"
    const val DELETED = "已删除"
    const val UPDATED = "已更新"
    const val RESTORED_DECK = "卡组已恢复"
    const val RESTORED_CARD = "卡片已恢复"
    const val ARCHIVED = "已归档，可在已归档内容中恢复"
    const val UNARCHIVED = "已恢复到卡组列表"

    // 备份恢复
    const val BACKUP_EXPORTED = "备份已导出"
    const val BACKUP_RESTORED = "恢复成功"
}
