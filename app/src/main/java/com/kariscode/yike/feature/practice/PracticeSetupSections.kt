package com.kariscode.yike.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kariscode.yike.domain.model.PracticeOrderMode
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeHeaderBlock
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 顶部 Hero 区持续强调“不影响正式调度”，是为了在开始练习前就消除用户对评分写入的误解。
 */
@Composable
fun PracticeHeroSection(
    uiState: PracticeSetupUiState,
    onOrderModeChange: (PracticeOrderMode) -> Unit,
    onStartPractice: () -> Unit
) {
    val spacing = LocalYikeSpacing.current
    YikeHeroCard(
        eyebrow = "Practice Mode",
        title = if (uiState.effectiveQuestionCount > 0) {
            "当前将练习 ${uiState.effectiveQuestionCount} 题"
        } else {
            "先调整范围再开始练习"
        },
        description = "这条流程只负责主动回忆与浏览答案，不会生成 ReviewRecord，也不会改动任何调度字段。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikeBadge(text = buildDeckScopeLabel(uiState))
            YikeBadge(text = buildCardScopeLabel(uiState))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikeBadge(text = buildQuestionScopeLabel(uiState))
            YikeBadge(text = if (uiState.orderMode == PracticeOrderMode.RANDOM) "随机出题" else "顺序出题")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            PracticeOrderMode.entries.forEach { mode ->
                FilterChip(
                    selected = uiState.orderMode == mode,
                    onClick = { onOrderModeChange(mode) },
                    label = { Text(mode.label) }
                )
            }
        }
        YikePrimaryButton(
            text = "开始练习",
            onClick = onStartPractice,
            enabled = uiState.effectiveQuestionCount > 0,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 当当前范围下没有题可练时，设置页需要给出可逆反馈，
 * 这样用户会理解为“选择过窄”而不是“页面坏了”。
 */
@Composable
fun PracticeEmptyStateSection(
    uiState: PracticeSetupUiState,
    onSelectAllQuestions: () -> Unit
) {
    if (uiState.effectiveQuestionCount > 0) {
        return
    }
    YikeStateBanner(
        title = "当前范围下没有可练习题目",
        description = "当前全局范围是“${buildDeckScopeLabel(uiState)} / ${buildCardScopeLabel(uiState)} / ${buildQuestionScopeLabel(uiState)}”，可以放宽任一层级后再开始。"
    ) {
        YikeSecondaryButton(
            text = "恢复当前题目全选",
            onClick = onSelectAllQuestions,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 卡组段落先展示顶层范围，是为了让用户优先从最可解释的内容分组开始缩圈。
 */
@Composable
fun PracticeDeckSection(
    deckOptions: List<PracticeDeckOptionUiModel>,
    onDeckToggle: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val spacing = LocalYikeSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        PracticeExpandableSectionCard(
            eyebrow = "Step 1",
            title = "选择卡组范围",
            subtitle = "不选卡组时默认包含全部 active 且未归档的内容。",
            summaryBadges = {
                YikeBadge(text = "${deckOptions.size} 个卡组")
            },
            expanded = expanded,
            onExpandedChange = onExpandedChange
        )
        if (expanded) {
            deckOptions.forEach { option ->
                YikeListItemCard(
                    title = option.deckName,
                    summary = "${option.cardCount} 张卡片 · ${option.questionCount} 个问题",
                    supporting = if (option.isSelected) "已加入本次练习范围。" else "点一下即可把整组内容纳入练习。"
                ) {
                    YikeSecondaryButton(
                        text = if (option.isSelected) "取消选择" else "加入练习",
                        onClick = { onDeckToggle(option.deckId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 卡片段落只展示当前 deck 范围下可见卡片，是为了让多层级选择保持清晰的父子关系。
 */
@Composable
fun PracticeCardSection(
    cardOptions: List<PracticeCardOptionUiModel>,
    onCardToggle: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val spacing = LocalYikeSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        PracticeExpandableSectionCard(
            eyebrow = "Step 2",
            title = "按卡片进一步缩圈",
            subtitle = "不选卡片时，默认练习当前卡组范围下的全部题目。",
            summaryBadges = {
                YikeBadge(text = "${cardOptions.size} 张卡片")
            },
            expanded = expanded,
            onExpandedChange = onExpandedChange
        )
        if (expanded) {
            if (cardOptions.isEmpty()) {
                YikeStateBanner(
                    title = "当前没有可选卡片",
                    description = "先选中至少一个有题目的卡组，或回到卡组页补充内容。"
                )
            } else {
                cardOptions.forEach { option ->
                    YikeListItemCard(
                        title = option.cardTitle,
                        summary = "${option.deckName} · ${option.questionCount} 个问题",
                        supporting = if (option.isSelected) "本卡片已加入练习范围。" else "适合做专项巩固或章节回顾。"
                    ) {
                        YikeSecondaryButton(
                            text = if (option.isSelected) "取消卡片" else "选择本卡",
                            onClick = { onCardToggle(option.cardId) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 题目级手选入口放在列表前，是为了把“全选 / 清空 / 当前总数”先说明清楚，再进入逐题取舍。
 */
@Composable
fun PracticeQuestionSectionHeader(
    uiState: PracticeSetupUiState,
    onSelectAllQuestions: () -> Unit,
    onClearQuestionSelection: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val spacing = LocalYikeSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
        PracticeExpandableSectionCard(
            eyebrow = "Step 3",
            title = "题目级手选",
            subtitle = "默认全选当前范围。若只想刷局部题集，可以在这里继续排除。",
            summaryBadges = {
                YikeBadge(text = "当前 ${uiState.effectiveQuestionCount} 题")
                YikeBadge(text = if (uiState.selectedQuestionIds == null) "全选" else "已手选")
            },
            expanded = expanded,
            onExpandedChange = onExpandedChange
        )
        if (expanded) {
            YikeSurfaceCard {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    YikeSecondaryButton(
                        text = "恢复全选",
                        onClick = onSelectAllQuestions,
                        modifier = Modifier.weight(1f)
                    )
                    YikeSecondaryButton(
                        text = "清空题目",
                        onClick = onClearQuestionSelection,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 可折叠段落把标题、摘要和展开动作收在同一张卡里，是为了让设置页先展示结构，再按需放出长列表。
 */
@Composable
private fun PracticeExpandableSectionCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    summaryBadges: @Composable (() -> Unit)? = null
) {
    val spacing = LocalYikeSpacing.current
    YikeSurfaceCard {
        YikeHeaderBlock(
            eyebrow = eyebrow,
            title = title,
            subtitle = subtitle
        )
        if (summaryBadges != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                summaryBadges()
            }
        }
        Text(
            text = if (expanded) "已展开，可直接调整这一段内容。" else "默认收起，点展开后再调整这一段内容。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        YikeSecondaryButton(
            text = if (expanded) "收起" else "展开",
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 单题卡片保留来源层级和答案预览，是为了让用户在手选时能快速判断题目是否值得纳入本次练习。
 */
@Composable
fun PracticeQuestionCard(
    option: PracticeQuestionOptionUiModel,
    onQuestionToggle: (String) -> Unit
) {
    YikeListItemCard(
        title = option.prompt,
        summary = "${option.deckName} / ${option.cardTitle}",
        supporting = "答案预览：${option.answerPreview}"
    ) {
        YikeSecondaryButton(
            text = if (option.isSelected) "移出本次练习" else "加入本次练习",
            onClick = { onQuestionToggle(option.questionId) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 卡组范围摘要始终可见，是为了让用户在折叠状态下也能理解当前练习会覆盖哪些顶层内容。
 */
private fun buildDeckScopeLabel(uiState: PracticeSetupUiState): String = when {
    uiState.deckOptions.isEmpty() -> "暂无卡组"
    uiState.selectedDeckIds.isEmpty() -> "全部卡组"
    else -> "已选 ${uiState.selectedDeckIds.size} 个卡组"
}

/**
 * 卡片范围摘要用“全部/已选 N 张”表达，是为了把二级范围预览压缩成用户一眼能懂的短语。
 */
private fun buildCardScopeLabel(uiState: PracticeSetupUiState): String = when {
    uiState.cardOptions.isEmpty() -> "暂无卡片"
    uiState.selectedCardIds.isEmpty() -> "全部卡片"
    else -> "已选 ${uiState.selectedCardIds.size} 张卡片"
}

/**
 * 题目范围摘要单独标出手选状态，是为了帮助用户判断当前题量是来自全选还是局部排除。
 */
private fun buildQuestionScopeLabel(uiState: PracticeSetupUiState): String = when {
    uiState.questionOptions.isEmpty() -> "0 题"
    uiState.selectedQuestionIds == null -> "${uiState.effectiveQuestionCount} 题全选"
    uiState.selectedQuestionIds.isEmpty() -> "已清空题目"
    else -> "手选 ${uiState.effectiveQuestionCount} 题"
}
