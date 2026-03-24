package com.kariscode.yike.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeProgressBar
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.format.formatPreviewDateTime
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 结果区改成惰性列表，是为了避免搜索命中很多问题时在首屏一次性组合全部卡片导致掉帧。
 */
internal fun LazyListScope.questionSearchResultItems(
    uiState: QuestionSearchUiState,
    onOpenEditor: (String) -> Unit,
    onOpenReview: (String) -> Unit,
    onOpenPractice: (PracticeSessionArgs) -> Unit
) {
    if (uiState.results.isEmpty()) {
        item {
            YikeStateBanner(
                title = "没有找到符合条件的问题",
                description = if (uiState.keyword.isNotBlank()) {
                    "已保留关键词“${uiState.keyword}”，可以继续微调它，或先清空熟练度和卡片筛选再扩大范围。"
                } else {
                    "当前筛选没有命中结果，可以继续保留这些条件，或先清空熟练度和卡片筛选再扩大范围。"
                }
            )
        }
        return
    }

    items(
        items = uiState.results,
        key = { item -> item.context.question.id }
    ) { item ->
        QuestionSearchResultCard(
            item = item,
            onOpenEditor = onOpenEditor,
            onOpenReview = onOpenReview,
            onOpenPractice = onOpenPractice
        )
    }
}

/**
 * 单条结果卡继续保留操作按钮和层级信息，是为了让惰性列表优化后不牺牲原有定位效率。
 */
@Composable
private fun QuestionSearchResultCard(
    item: QuestionSearchResultUiModel,
    onOpenEditor: (String) -> Unit,
    onOpenReview: (String) -> Unit,
    onOpenPractice: (PracticeSessionArgs) -> Unit
) {
    val spacing = LocalYikeSpacing.current
    YikeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.context.question.prompt,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            YikeBadge(text = item.mastery.level.label)
        }
        Text(
            text = buildAnswerSnippet(item),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            item.context.question.tags.forEach { tag ->
                YikeBadge(text = tag)
            }
        }
        Text(
            text = "${item.context.deckName} / ${item.context.cardTitle}",
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = buildMetaLine(item),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        YikeProgressBar(progress = item.mastery.progress)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikeSecondaryButton(
                text = "练习这题",
                onClick = {
                    onOpenPractice(
                        PracticeSessionArgs(
                            deckIds = listOf(item.context.deckId),
                            cardIds = listOf(item.context.question.cardId),
                            questionIds = listOf(item.context.question.id)
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            )
            YikePrimaryButton(
                text = if (item.isDue) "立即复习" else "查看卡片",
                onClick = {
                    if (item.isDue) onOpenReview(item.context.question.cardId) else onOpenEditor(item.context.question.cardId)
                },
                modifier = Modifier.weight(1f)
            )
        }
        YikeSecondaryButton(
            text = "编辑问题",
            onClick = { onOpenEditor(item.context.question.cardId) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 答案片段统一截断，是为了让结果列表先帮助定位内容，而不是一屏被完整答案撑满。
 */
private fun buildAnswerSnippet(item: QuestionSearchResultUiModel): String {
    val answer = item.context.question.answer.ifBlank { "尚未填写答案。" }
    val snippet = answer.take(60)
    return if (answer.length > 60) "$snippet..." else snippet
}

/**
 * 元信息统一描述状态、复习次数和最近复习时间，是为了让用户在一个视线范围内判断是否值得现在处理。
 */
private fun buildMetaLine(item: QuestionSearchResultUiModel): String {
    val question = item.context.question
    val statusText = question.status.displayLabel
    val reviewedAtText = question.lastReviewedAt?.let(::formatPreviewDateTime) ?: "尚未复习"
    val masteryHint = when (item.mastery.level) {
        QuestionMasteryLevel.NEW -> "新问题"
        QuestionMasteryLevel.LEARNING -> "仍在巩固"
        QuestionMasteryLevel.FAMILIAR -> "进入稳定区"
        QuestionMasteryLevel.MASTERED -> "掌握较稳"
    }
    return "$statusText · 复习 ${question.reviewCount} 次 · lapse ${question.lapseCount} 次 · 最近复习：$reviewedAtText · $masteryHint"
}
