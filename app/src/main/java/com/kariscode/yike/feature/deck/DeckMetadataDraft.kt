package com.kariscode.yike.feature.deck

import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import com.kariscode.yike.feature.common.ValidationMessageDraft

/**
 * 卡组编辑草稿单独承载间隔次数，是为了把“卡组元信息”和“卡片元信息”从现在开始区分开，
 * 避免共享草稿模型在新需求到来后不断堆叠只对单一页面生效的字段。
 */
data class DeckMetadataDraft(
    val entityId: String?,
    val name: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val intervalStepCountText: String = ReviewSchedulerV1.DEFAULT_INTERVAL_STEP_COUNT.toString(),
    val validationMessage: String? = null
) : ValidationMessageDraft<DeckMetadataDraft> {
    /**
     * 名称变更时同步清空旧校验提示，是为了让用户修正后能立即看到“当前输入重新有效”的反馈。
     */
    fun updateName(value: String): DeckMetadataDraft =
        copy(name = value, validationMessage = null)

    /**
     * 描述虽然不是必填，但和其他字段共享同一清理策略能避免弹窗残留过期错误文案。
     */
    fun updateDescription(value: String): DeckMetadataDraft =
        copy(description = value, validationMessage = null)

    /**
     * 标签进入草稿后，补全建议和删除操作都能围绕同一份待保存状态工作，避免 UI 另存副本。
     */
    fun updateTags(value: List<String>): DeckMetadataDraft =
        copy(tags = value, validationMessage = null)

    /**
     * 间隔次数文本先按字符串保存，是为了允许用户处于“正在输入中”的中间态，而不是每击键一次就强行丢弃。
     */
    fun updateIntervalStepCountText(value: String): DeckMetadataDraft =
        copy(intervalStepCountText = value, validationMessage = null)

    /**
     * 校验消息由草稿单点回填，可保证创建与编辑两条路径使用完全一致的提示口径。
     */
    override fun withValidationMessage(message: String?): DeckMetadataDraft =
        copy(validationMessage = message)
}
