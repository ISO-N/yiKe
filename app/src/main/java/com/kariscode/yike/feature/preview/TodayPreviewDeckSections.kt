package com.kariscode.yike.feature.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.format.formatPreviewDay
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * 卡组列表维持“卡组 -> 卡片 -> 题目预览”的顺序，是为了贴合用户开始前的真实浏览路径。
 */
@Composable
internal fun TodayPreviewDeckSection(
    deckGroups: List<TodayPreviewDeckUiModel>
) {
    val spacing = LocalYikeSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        deckGroups.forEach { deck ->
            YikeSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(text = deck.deckName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${deck.dueQuestionCount} 题 · 约 ${deck.estimatedMinutes} 分钟",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    YikeBadge(
                        text = if (deck.lowMasteryCount > 0) "${deck.lowMasteryCount} 题低熟练度" else "节奏稳定"
                    )
                }
                deck.cards.forEach { card ->
                    TodayPreviewCardSection(card = card)
                }
            }
        }
        Spacer(modifier = Modifier.height(spacing.section))
    }
}

/**
 * 卡片区只展示前几道题的预览文本，是为了让用户建立内容预期但不在开始前被细节淹没。
 */
@Composable
private fun TodayPreviewCardSection(
    card: TodayPreviewCardUiModel
) {
    val spacing = LocalYikeSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = card.cardTitle, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${card.dueQuestionCount} 题到期 · 约 ${card.estimatedMinutes} 分钟",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        card.questions.forEach { question ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = question.prompt,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                YikeBadge(text = question.mastery.level.label)
            }
            Text(
                text = formatPreviewDay(question.dueAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (card.lowMasteryCount > 0) {
            Text(
                text = "其中 ${card.lowMasteryCount} 题仍处于${QuestionMasteryLevel.LEARNING.label}阶段，建议优先处理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

