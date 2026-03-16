package com.kariscode.yike.feature.common

/**
 * 卡组和卡片的元信息维护本质上都是“主字段 + 补充说明”的轻量表单，
 * 抽成共享草稿后可以让两个管理页围绕同一套校验和弹窗约定演进，避免结构慢慢漂移。
 */
data class TextMetadataDraft(
    val entityId: String?,
    val primaryValue: String,
    val secondaryValue: String,
    val validationMessage: String? = null
) {
    /**
     * 主字段修改时统一清空旧校验提示，是为了让用户修正输入后立即回到干净的编辑状态。
     */
    fun updatePrimaryValue(value: String): TextMetadataDraft =
        copy(primaryValue = value, validationMessage = null)

    /**
     * 补充说明虽然不是必填，但仍需要和主字段共享同一套反馈清理规则，避免弹窗残留过期提示。
     */
    fun updateSecondaryValue(value: String): TextMetadataDraft =
        copy(secondaryValue = value, validationMessage = null)

    /**
     * 校验消息由单点覆盖，是为了让不同管理页在“保存失败后如何回填提示”上保持一致口径。
     */
    fun withValidationMessage(message: String?): TextMetadataDraft =
        copy(validationMessage = message)
}
